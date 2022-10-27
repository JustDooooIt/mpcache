package io.github.justdooooit.mpcache.cache.impl;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.justdooooit.mpcache.cache.Cache;
import io.github.justdooooit.mpcache.command.ICommand;
import io.github.justdooooit.mpcache.command.impl.CachePutCommand;
import io.github.justdooooit.mpcache.command.impl.CacheRemoveCommand;
import io.github.justdooooit.mpcache.factory.MapperFactory;
import io.github.justdooooit.mpcache.interceptor.CacheUpdateInterceptor;
import io.github.justdooooit.mpcache.utils.ReflectUtils;
import io.github.justdooooit.mpcache.wrapper.CacheLambdaQueryWrapper;
import io.github.justdooooit.mpcache.base.Key;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class DefaultCache implements Cache {

    @Value("${mybatis-plus.cache-size}")
    private Integer cacheSize = 16;

    @Resource
    private MapperFactory mapperFactory;

    /**
     * 记录当前线程是否执行更新操作，当拦截更新操作时，
     * 判断ThreadLocal是否存在数据，存在即当前线程正
     * 在更新缓存以及数据库数据，不清除缓存
     */
    private final ThreadLocal<Object> isUpdate = new ThreadLocal<>();

    @Resource
    private CacheUpdateInterceptor cacheUpdateInterceptor;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private Map<Key, Object> map;

    @Resource
    private ThreadLocal<Deque<ICommand>> commands;

    private final Map<Key, String> fieldNameMap = new ConcurrentHashMap<>();

    private final Object PRESENT = new Object();

    @PostConstruct
    public void init() {
        map = Collections.synchronizedMap(new WeakHashMap<>());
    }

    @Override
    @SneakyThrows
    public <T> boolean add(T o) {
        Lock writeLock = readWriteLock.writeLock();
        try {
            writeLock.lock();
            isUpdate.set(PRESENT);
            Serializable id = ReflectUtils.getId(o);
            BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass());
            if (id == null) {
                baseMapper.insert(o);
                id = ReflectUtils.getId(o);
                o = baseMapper.selectById(id);
                ICommand command = new CachePutCommand(map, new Key(o.getClass(), id), createProxy(o));
                if (commands.get() != null) {
                    commands.get().push(command);
                } else {
                    command.execute();
                }
                return true;
            } else {
                Key key = new Key(o.getClass(), id);
                if (map.containsKey(key)) {
                    return false;
                } else {
                    T dbResult = baseMapper.selectById(id);
                    if (dbResult == null) {
                        baseMapper.insert(o);
                        id = ReflectUtils.getId(o);
                        o = baseMapper.selectById(id);
                        key = new Key(o.getClass(), id);
                        ICommand command = new CachePutCommand(map, key, createProxy(o));
                        if (commands.get() != null) {
                            commands.get().push(command);
                        } else {
                            command.execute();
                        }
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        } finally {
            isUpdate.remove();
            writeLock.unlock();
        }
    }

    @Override
    @SneakyThrows
    @Transactional
    public <T> boolean remove(@NotNull T o) {
        Lock writeLock = readWriteLock.writeLock();
        try {
            writeLock.lock();
            isUpdate.set(PRESENT);
            BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass().getSuperclass());
            int row = baseMapper.deleteById(o);
            if (row == 0) {
                return false;
            } else {
                if (Enhancer.isEnhanced(o.getClass())) {
                    Key key = new Key(o.getClass().getSuperclass(), ReflectUtils.getId(o));
                    ICommand command = new CacheRemoveCommand(map, key, o);
                    if (commands.get() != null) {
                        commands.get().push(command);
                    } else {
                        command.execute();
                    }
                    return !map.containsKey(key);
                } else {
                    Key key = new Key(o.getClass(), ReflectUtils.getId(o));
                    ICommand command = new CacheRemoveCommand(map, key, o);
                    if (commands.get() != null) {
                        commands.get().push(command);
                    } else {
                        command.execute();
                    }
                    return !map.containsKey(key);
                }
            }
        } finally {
            isUpdate.remove();
            writeLock.unlock();
        }
    }

    @Override
    @SneakyThrows
    public <T> boolean contains(T o) {
        Lock readLock = readWriteLock.readLock();
        try{
            readLock.lock();
            if (Enhancer.isEnhanced(o.getClass())) {
                if (map.containsKey(new Key(o.getClass().getSuperclass(), ReflectUtils.getId(o)))) {
                    return true;
                } else {
                    BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass().getSuperclass());
                    o = baseMapper.selectById(ReflectUtils.getId(o));
                    return o != null;
                }
            } else {
                if (map.containsKey(new Key(o.getClass(), ReflectUtils.getId(o)))) {
                    return true;
                } else {
                    BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass());
                    o = baseMapper.selectById(ReflectUtils.getId(o));
                    return o != null;
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    @SneakyThrows
    @Override
    public <T, ID extends Serializable> T get(Class<T> clazz, ID id) {
        Lock readLock = readWriteLock.readLock();
        try {
            readLock.lock();
            Key key = new Key(clazz, id);
            T result = (T) map.get(key);
            if (result == null) {
                BaseMapper<T> baseMapper = mapperFactory.getMapper(clazz);
                result = baseMapper.selectById(id);
                if (result != null) {
                    map.put(key, result);
                }
            }
            return result;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    @SneakyThrows
    public <T> List<T> find(Class<T> clazz, @NotNull CacheLambdaQueryWrapper<T> wrapper) {
        Lock readLock = readWriteLock.readLock();
        try {
            readLock.lock();
            BaseMapper<T> baseMapper = mapperFactory.getMapper(clazz);
            List<T> list = new ArrayList<>();
            map.forEach((key, value) -> {
                if (key.getClazz() == clazz) {
                    list.add((T) value);
                }
            });
            List<T> cacheResultList = list.stream().filter(wrapper.getPredicate()).collect(Collectors.toList());
            CacheLambdaQueryWrapper<T> copyWrapper = wrapper.clone().selectAll();
            Long count = baseMapper.selectCount(copyWrapper);
            if (cacheResultList.size() != count) {
                List<T> dbResult = baseMapper.selectList(wrapper);
                cacheResultList = createProxyByList(dbResult);
                for (T t : cacheResultList) {
                    Key key = new Key(t.getClass().getSuperclass(), ReflectUtils.getId(t));
                    map.put(key, t);
                    fieldNameMap.put(key, wrapper.getFieldSelect());
                }
                return cacheResultList;
            } else {
                String fieldSelect = wrapper.getFieldSelect();
                boolean isSameSelect = true;
                for (T t : cacheResultList) {
                    Serializable id = ReflectUtils.getId(t);
                    Key key = new Key(t.getClass().getSuperclass(), id);
                    if (!Objects.equals(fieldNameMap.get(key), fieldSelect)) {
                        isSameSelect = false;
                        break;
                    }
                }
                if (isSameSelect) {
                    return cacheResultList;
                } else {
                    List<T> dbResult = baseMapper.selectList(wrapper);
                    for (T t : dbResult) {
                        Key key = new Key(t.getClass(), ReflectUtils.getId(t));
                        fieldNameMap.put(key, wrapper.getFieldSelect());
                    }
                    List<T> proxyList =  createProxyByList(dbResult);
                    for (T t : proxyList) {
                        map.put(new Key(t.getClass().getSuperclass(), ReflectUtils.getId(t)), t);
                    }
                    return proxyList;
                }
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public void clearCache() {
        Lock writeLock = readWriteLock.writeLock();
        try {
            writeLock.lock();
            map.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public ThreadLocal<Object> getIsUpdate() {
        return isUpdate;
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return readWriteLock;
    }

    @SneakyThrows
    private <T> List<T> createProxyByList(List<T> list) {
        List<T> result = new ArrayList<>();
        for (T data : list) {
            Key key = new Key(data.getClass(), ReflectUtils.getId(data));
            Object proxy = createProxy(data);
            result.add((T) proxy);
        }
        return result;
    }

    private Object createProxy(Object o) throws IllegalAccessException {
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(o.getClass());
        enhancer.setCallback(cacheUpdateInterceptor);
        List<Field> fields = FieldUtils.getAllFieldsList(o.getClass());
        Object proxy = enhancer.create();
        for (Field field : fields) {
            FieldUtils.writeField(field, proxy, FieldUtils.readField(field, o, true), true);
        }
        return proxy;
    }
}
