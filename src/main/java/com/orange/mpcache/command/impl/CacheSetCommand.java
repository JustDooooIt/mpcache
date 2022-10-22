package com.orange.mpcache.command.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.mpcache.command.ICommand;
import com.orange.mpcache.factory.MapperFactory;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodProxy;

import java.util.Locale;

public  class CacheSetCommand implements ICommand {

    private final Object o;

    private final MethodProxy methodProxy;

    private final Object newValue;

    private final Object oldValue;

    private final MapperFactory mapperFactory;

    @SneakyThrows
    public CacheSetCommand(Object o, MethodProxy methodProxy, Object newValue, MapperFactory mapperFactory) {
        this.o = o;
        this.methodProxy = methodProxy;
        this.newValue = newValue;
        this.mapperFactory = mapperFactory;

        StringBuilder fieldNameBuilder = new StringBuilder(methodProxy.getSignature().getName().substring(3));
        String fieldName = fieldNameBuilder.replace(0, 1, fieldNameBuilder.substring(0, 1).toLowerCase(Locale.ROOT)).toString();
        this.oldValue = FieldUtils.readField(o, fieldName, true);
    }

    @SneakyThrows
    @Override
    public void execute() {
        methodProxy.invokeSuper(o, new Object[]{ newValue });
    }

    @SneakyThrows
    @Override
    public void undo() {
        methodProxy.invokeSuper(o, new Object[]{ oldValue });
    }
}
