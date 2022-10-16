package com.orange.mpcache.factory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.io.IOException;

public interface MapperFactory {

    <T> BaseMapper<T> getMapper(Class<?> genericType) throws IOException, ClassNotFoundException;
}
