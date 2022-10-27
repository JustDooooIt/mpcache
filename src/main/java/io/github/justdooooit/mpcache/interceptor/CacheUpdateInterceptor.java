package io.github.justdooooit.mpcache.interceptor;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.justdooooit.mpcache.cache.Cache;
import io.github.justdooooit.mpcache.command.ICommand;
import io.github.justdooooit.mpcache.command.impl.CacheSetCommand;
import io.github.justdooooit.mpcache.factory.MapperFactory;
import io.github.justdooooit.mpcache.utils.ReflectUtils;
import lombok.SneakyThrows;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;

import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.List;

public class CacheUpdateInterceptor implements MethodInterceptor {

    @Resource
    private Cache cache;

    @Resource
    private MapperFactory mapperFactory;

    private final Object obj = new Object();

    @Resource(name = "commands")
    private ThreadLocal<Deque<ICommand>> commands;

    @Override
    public synchronized Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        try {
            cache.getIsUpdate().set(obj);
            Object result = null;
            if (canAssigned(method)) {
                ICommand cacheSetCommand = new CacheSetCommand(o, methodProxy, objects[0], mapperFactory);
                if (commands.get() != null) {
                    commands.get().push(cacheSetCommand);
                } else {
                    cacheSetCommand.execute();
                }
                updateDB(o, objects[0], method);
            } else {
                result = methodProxy.invokeSuper(o, objects);
            }
            return result;
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

    private Field getIdField(Class<?> clazz) {
        List<Field> fields = FieldUtils.getFieldsListWithAnnotation(clazz, TableId.class);
        if (fields.size() == 0) {
            throw new RuntimeException("主键不存在，请设置主键");
        } else {
            return fields.get(0);
        }
    }

    @SneakyThrows
    private void updateDB(Object o, Object value, Method method) {
        Object dbo = o.getClass().newInstance();
        Field idField = getIdField(dbo.getClass());
        FieldUtils.writeField(idField, dbo, ReflectUtils.getId(o), true);
        Field field = FieldUtils.getField(dbo.getClass(), getFieldNameFromMethodName(method.getName()), true);
        FieldUtils.writeField(field, dbo, value, true);
        BaseMapper<Object> baseMapper = mapperFactory.getMapper(Enhancer.isEnhanced(o.getClass()) ? o.getClass().getSuperclass() : o.getClass());
        baseMapper.updateById(dbo);
    }
}
