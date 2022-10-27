package io.github.justdooooit.mpcache.interceptor;

import io.github.justdooooit.mpcache.cache.Cache;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;

import javax.annotation.Resource;

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
