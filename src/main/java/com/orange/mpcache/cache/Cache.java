package com.orange.mpcache.cache;

import com.orange.mpcache.wrapper.CacheLambdaQueryWrapper;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

public interface Cache {
    <T> boolean add(T o);

    <T> boolean remove(@NotNull T o);

    <T> boolean contains(T o);

    <T,  ID extends Serializable> T get(Class<T> clazz, ID id);

    <T> List<T> find(Class<T> clazz, @NotNull CacheLambdaQueryWrapper<T> wrapper);

    void clearCache();

    ThreadLocal<Object> getIsUpdate();

    ReadWriteLock getReadWriteLock();
}
