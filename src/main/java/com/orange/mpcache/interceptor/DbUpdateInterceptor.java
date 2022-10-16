package com.orange.mpcache.interceptor;

import com.orange.mpcache.cache.Cache;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Intercepts(
        @Signature(method = "update", type = Executor.class, args = { MappedStatement.class, Object.class })
)
public class DbUpdateInterceptor implements Interceptor {

    @Resource
    private Cache cache;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (cache.getIsUpdate().get() == null) {
            cache.clearCache();
        }
        return invocation.proceed();
    }
}
