package com.orange.mpcache.cache;

import com.orange.mpcache.utils.CacheLambdaQueryWrapper;
import lombok.SneakyThrows;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface Cache {
    <T> boolean add(T o);

    <T> boolean remove(@NotNull T o);

    <T> boolean contains(T o);

    <T> T get(Class<T> clazz, Object id);

    <T> List<T> find(Class<T> clazz, @NotNull CacheLambdaQueryWrapper<T> wrapper);

    void clearCache();

    ThreadLocal<Object> getIsUpdate();
}
