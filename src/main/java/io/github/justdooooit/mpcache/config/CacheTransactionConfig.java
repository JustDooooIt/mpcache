package io.github.justdooooit.mpcache.config;

import io.github.justdooooit.mpcache.command.ICommand;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Transactional;

import java.util.Deque;

public class CacheTransactionConfig {

    @Bean(name = "commands")
    public ThreadLocal<Deque<ICommand>> commands() {
        return new ThreadLocal<>();
    }

    @Bean(name = "isSaveCommand")
    public ThreadLocal<Object> isSaveCommand() {
        return new ThreadLocal<>();
    }

    @Bean(name = "transactionalQueue")
    public ThreadLocal<Deque<Transactional>> transactionalQueue() {
        return new ThreadLocal<>();
    }
}
