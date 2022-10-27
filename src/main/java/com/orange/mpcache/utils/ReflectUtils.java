package com.orange.mpcache.utils;

import com.baomidou.mybatisplus.annotation.TableId;
import org.apache.commons.lang3.reflect.FieldUtils;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.List;

public class ReflectUtils {
    public static Serializable getId(Object o) throws IllegalAccessException {
        List<Field> fields = FieldUtils.getFieldsListWithAnnotation(o.getClass(), TableId.class);
        if (fields.size() == 0) {
            throw new RuntimeException("主键不存在，请设置主键");
        }
        return (Serializable) FieldUtils.readField(fields.get(0), o, true);
    }
}
