package com.orange.mpcache.utils;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Key {
    private Class<?> clazz;
    private Object id;
}