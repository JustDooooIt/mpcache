package com.orange.mpcache.factory.impl;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.orange.mpcache.factory.MapperFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultMapperFactory implements MapperFactory {

    private final List<Class<BaseMapper<?>>> mapperClassList = new ArrayList<>();

    @Value("${mybatis-plus.mapper-class-locations}")
    private String[] mapperLocations = new String[]{"com.example.mybatisdemo.mapper"};

    @Resource
    private ApplicationContext applicationContext;

    @PostConstruct
    public void initMapperList() throws IOException, ClassNotFoundException {
        if (mapperLocations.length == 0) {
            throw new RuntimeException("找不到mapper文件，请配置mapper-class-locations");
        }
        for (String location: mapperLocations) {
            for (org.springframework.core.io.Resource resource : applicationContext.getResources(location.replace(".", "/") + "/**")) {
                String className;
                if (StringUtils.isBlank(resource.getFilename()) || StringUtils.isBlank(className = resource.getFilename().split("\\.")[0])) {
                    throw new RuntimeException("文件名为空，请检查文件名");
                }
                Class<BaseMapper<?>> clazz = (Class<BaseMapper<?>>) Class.forName(location + "." + className);
                mapperClassList.add(clazz);
            }
        }
    }

    /**
     * 通过mapper的泛型查找mapper
     * @param genericType
     * @return
     */
    @Override
    public <T> BaseMapper<T> getMapper(Class<?> genericType) {
        for (Class<BaseMapper<?>> mapperClass: mapperClassList) {
            if (((ParameterizedType) mapperClass.getGenericInterfaces()[0]).getActualTypeArguments()[0] == genericType) {
                return (BaseMapper<T>) applicationContext.getBean(mapperClass);
            }
        }
        throw new RuntimeException("mapper不存在，请创建mapper");
    }
}
