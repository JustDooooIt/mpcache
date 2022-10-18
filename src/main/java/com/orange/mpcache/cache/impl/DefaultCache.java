package com.orange.mpcache.cache.impl;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.mpcache.annotation.ConstructorExtends;
import com.orange.mpcache.cache.Cache;
import com.orange.mpcache.factory.MapperFactory;
import com.orange.mpcache.interceptor.CacheUpdateInterceptor;
import com.orange.mpcache.utils.FixedLinkedHashMap;
import com.orange.mpcache.wrapper.CacheLambdaQueryWrapper;
import com.orange.mpcache.base.Key;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Component
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

    private CacheUpdateInterceptor cacheUpdateInterceptor;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private Map<Key, Object> map;

    private final Object PRESENT = new Object();

    @PostConstruct
    public void init() {
        map = new FixedLinkedHashMap<>(cacheSize);
        cacheUpdateInterceptor = new CacheUpdateInterceptor(this, mapperFactory);
    }

    @Override
    @SneakyThrows
    @Transactional
    public <T> boolean add(T o) {
        Lock writeLock = readWriteLock.writeLock();
        try {
            writeLock.lock();
            isUpdate.set(PRESENT);
            Serializable id = getId(o);
            BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass());
            if (id == null) {
                baseMapper.insert(o);
                id = getId(o);
                o = baseMapper.selectById(id);
                map.put(new Key(o.getClass(), id), createProxy(o));
                return true;
            } else {
                Key key = new Key(o.getClass(), id);
                if (map.containsKey(key)) {
                    return false;
                } else {
                    T dbResult = baseMapper.selectById(id);
                    if (dbResult == null) {
                        baseMapper.insert(o);
                        id = getId(o);
                        key = new Key(o.getClass(), id);
                        map.put(key, o);
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
        if (!Enhancer.isEnhanced(o.getClass())) {
            return false;
        }
        Lock writeLock = readWriteLock.writeLock();
        try {
            writeLock.lock();
            isUpdate.set(PRESENT);
            if (Enhancer.isEnhanced(o.getClass())) {
                BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass().getSuperclass());
                baseMapper.deleteById(o);
                return map.remove(new Key(o.getClass().getSuperclass(), getId(o)), o);
            } else {
                BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass());
                baseMapper.deleteById(o);
                return map.remove(new Key(o.getClass(), getId(o)), o);
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
                if (map.containsKey(new Key(o.getClass().getSuperclass(), getId(o)))) {
                    return true;
                } else {
                    BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass().getSuperclass());
                    o = baseMapper.selectById(getId(o));
                    return o != null;
                }
            } else {
                if (map.containsKey(new Key(o.getClass(), getId(o)))) {
                    return true;
                } else {
                    BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass());
                    o = baseMapper.selectById(getId(o));
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
                List<T> dbResult = baseMapper.selectList(copyWrapper);
                cacheResultList = getProxyByList(dbResult);
            }
            Map<Field, Boolean> fieldMatchMap = new HashMap<>();
            cacheResultList = cacheResultList.stream().peek(t -> {
                if (wrapper.getFieldList().size() > 0) {
                    List<Field> fields = FieldUtils.getAllFieldsList(t.getClass());
                    for (Field field : fields) {
                        fieldMatchMap.put(field, false);
                    }
                    for (Field field : fields) {
                        for (String selectField : wrapper.getFieldList()) {
                            if (field.getName().equals(selectField)) {
                                fieldMatchMap.put(field, true);
                                break;
                            }
                        }
                    }
                    for (Field field : fieldMatchMap.keySet()) {
                        boolean isMatch = fieldMatchMap.get(field);
                        TableField tableField = field.getAnnotation(TableField.class);
                        if (!isMatch && !field.getName().contains("CGLIB$") && (tableField == null || tableField.exist())) {
                            try {
                                FieldUtils.writeField(field, t, null, true);
                            } catch (IllegalAccessException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                    fieldMatchMap.clear();
                }
            }).collect(Collectors.toList());
            return cacheResultList;
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

    @SneakyThrows
    private <T> List<T> getProxyByList(List<T> list) {
        List<T> result = new ArrayList<>();
        for (T data : list) {
            Key key = new Key(data.getClass(), getId(data));
            Object proxy = createProxy(data);
            map.put(key, proxy);
            result.add((T) proxy);
        }
        return result;
    }

    private <T> Serializable getId(T o) throws IllegalAccessException {
        List<Field> fields = FieldUtils.getFieldsListWithAnnotation(o.getClass(), TableId.class);
        if (fields.size() == 0) {
            throw new RuntimeException("主键不存在，请设置主键");
        }
        return (Serializable) FieldUtils.readField(fields.get(0), o, true);
    }

    private void initEnhancerProperties(@NotNull Object o, List<Class<?>> typeList, List<Object> dataList) throws InvocationTargetException, IllegalAccessException {
        Constructor<?>[] constructors = o.getClass().getConstructors();
        for (Constructor<?> constructor: constructors) {
            if (constructor.getAnnotation(ConstructorExtends.class) != null) {
                for (Parameter parameter : constructor.getParameters()) {
                    typeList.add(parameter.getType());
                    dataList.add(FieldUtils.readField(o, parameter.getName(), true));
                }
                break;
            }
        }
    }

    private Object createProxy(Object o) throws InvocationTargetException, IllegalAccessException {
        List<Class<?>> typeList = new LinkedList<>();
        List<Object> dataList = new LinkedList<>();
        initEnhancerProperties(o, typeList, dataList);
        Enhancer enhancer = new Enhancer();
        enhancer.setSuperclass(o.getClass());
        enhancer.setCallback(cacheUpdateInterceptor);
        return enhancer.create(typeList.toArray(new Class<?>[0]), dataList.toArray());
    }
}
