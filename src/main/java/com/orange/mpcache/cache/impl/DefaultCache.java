package com.orange.mpcache.cache.impl;

import cn.hutool.core.map.FixedLinkedHashMap;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.mpcache.annotation.ConstructorExtends;
import com.orange.mpcache.cache.Cache;
import com.orange.mpcache.factory.MapperFactory;
import com.orange.mpcache.interceptor.CacheUpdateInterceptor;
import com.orange.mpcache.utils.CacheLambdaQueryWrapper;
import com.orange.mpcache.utils.Key;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class DefaultCache implements Cache {

    @Value("${mybatis-plus.cache-size}")
    private Integer cacheSize = 16;

    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    private Map<Key, Object> map;

    @Resource
    private MapperFactory mapperFactory;

    private final ThreadLocal<Object> isUpdate = new ThreadLocal<>();

    @Resource
    private CacheUpdateInterceptor cacheUpdateInterceptor;

    @PostConstruct
    public void init() {
        map = new FixedLinkedHashMap<>(cacheSize);
    }

    @Override
    @SneakyThrows
    @Transactional
    public <T> boolean add(T o) {
        Lock writeLock = readWriteLock.writeLock();
        try {
            writeLock.lock();
            isUpdate.set(new Object());
            Serializable id = getId(o);
            BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass());
            Key key;
            if (id == null) {
                baseMapper.insert(o);
                id = getId(o);
                o = baseMapper.selectById(id);
                key = new Key(o.getClass(), id);
            } else {
                key = new Key(o.getClass(), id);
                if (map.containsKey(key)) {
                    return false;
                } else {
                    boolean isExist = baseMapper.selectById(id) != null;
                    if (!isExist) {
                        baseMapper.insert(o);
                        id = getId(o);
                        key = new Key(o.getClass(), id);
                    }
                }
                o = baseMapper.selectById(id);
            }
            map.put(key, createProxy(o));
            return true;
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
            isUpdate.set(new Object());
            BaseMapper<T> baseMapper = mapperFactory.getMapper(o.getClass().getSuperclass());
            baseMapper.deleteById(o);
            return map.remove(new Key(o.getClass().getSuperclass(), getId(o)), o);
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
            return map.containsKey(new Key(o.getClass().getSuperclass(), getId(o)));
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public <T> T get(Class<T> clazz, Object id) {
        Lock readLock = readWriteLock.readLock();
        try {
            readLock.lock();
            return (T) map.get(new Key(clazz, id));
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
            List<T> list = new ArrayList<>();
            map.forEach((key, value) -> {
                if (key.getClazz() == clazz) {
                    list.add((T) value);
                }
            });
            List<T> cacheResultList = list.stream().filter(wrapper.getPredicate()).collect(Collectors.toList());
            BaseMapper<T> baseMapper = mapperFactory.getMapper(clazz);
            Long count = baseMapper.selectCount(wrapper);
            if (count != cacheResultList.size()) {
                List<T> dataList = baseMapper.selectList(wrapper);
                cacheResultList.clear();
                for (T data : dataList) {
                    Object proxy = createProxy(data);
                    map.put(new Key(data.getClass(), getId(data)), proxy);
                    cacheResultList.add((T) proxy);
                }
            }
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
