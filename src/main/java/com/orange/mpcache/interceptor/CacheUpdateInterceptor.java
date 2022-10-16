package com.orange.mpcache.interceptor;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.mpcache.cache.Cache;
import com.orange.mpcache.factory.MapperFactory;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@Component
public class CacheUpdateInterceptor implements MethodInterceptor {

    @Resource
    private Cache cache;

    @Resource
    private MapperFactory mapperFactory;

    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        try {
            cache.getIsUpdate().set(new Object());
            Object obj = methodProxy.invokeSuper(o, objects);
            if (canAssigned(method)) {
                BaseMapper<Object> baseMapper = mapperFactory.getMapper(Enhancer.isEnhanced(o.getClass()) ? o.getClass().getSuperclass() : o.getClass());
                baseMapper.updateById(o);
            }
            return obj;
        } finally {
            cache.getIsUpdate().remove();
        }
    }

    /**
     * 判断是否拦截set方法
     * @param method
     * @return
     */
    private boolean canAssigned(Method method) {
        String methodName = method.getName();
        if (methodName.contains("set")) {
            String fieldName = getFieldNameFromMethodName(methodName);
            Field field = FieldUtils.getField(method.getDeclaringClass(), fieldName, true);
            TableField tableField = field.getAnnotation(TableField.class);
            if (tableField == null) {
                return true;
            } else {
                return tableField.exist();
            }
        } else {
            return false;
        }
    }

    /**
     * 从set方法中获取字段名称
     * @param methodName
     * @return
     */
    private String getFieldNameFromMethodName(String methodName) {
        StringBuilder fieldNameBuilder = new StringBuilder();
        return fieldNameBuilder.append(methodName.substring(3))
                .replace(0, 1, fieldNameBuilder.substring(0, 1).toLowerCase())
                .toString();
    }
}
