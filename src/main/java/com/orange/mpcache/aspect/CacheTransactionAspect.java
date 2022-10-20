package com.orange.mpcache.aspect;

import com.orange.mpcache.cache.Cache;
import com.orange.mpcache.command.ICommand;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Deque;
import java.util.concurrent.ConcurrentLinkedDeque;

@Aspect
public class CacheTransactionAspect {

    @Resource
    private Cache cache;

    @Resource(name = "commands")
    private ThreadLocal<Deque<ICommand>> commands;

    @Resource
    private ThreadLocal<Deque<Transactional>> transactionalQueue;

    private final ThreadLocal<Deque<ICommand>> undoCommands = new ThreadLocal<>();

    @Pointcut("@annotation(org.springframework.transaction.annotation.Transactional)")
    public void pointCut() {}

    @AfterThrowing(value = "pointCut() && @annotation(transactional)", throwing = "e")
    public void throwAfter(Transactional transactional, RuntimeException e) {
        Propagation propagation = transactional.propagation();
        if (propagation == Propagation.REQUIRED) {
            while (!undoCommands.get().isEmpty()) {
                undoCommands.get().pop().undo();
            }
            commands.remove();
            undoCommands.remove();
            throw e;
        }
    }

    /**
     * 初始化ThreadLocal,
     */
    @Before(value = "pointCut() && @annotation(transactional)")
    public void before(Transactional transactional) {
        if (commands.get() == null) {
            commands.set(new ConcurrentLinkedDeque<>());
        }
        if (undoCommands.get() == null) {
            undoCommands.set(new ConcurrentLinkedDeque<>());
        }
        if (transactionalQueue.get() == null) {
            transactionalQueue.set(new ConcurrentLinkedDeque<>());
        }
    }

    @AfterReturning(value = "pointCut() && @annotation(transactional)")
    public void after(JoinPoint joinPoint, Transactional transactional) {
        Propagation propagation = transactional.propagation();
        if (propagation == Propagation.REQUIRED) {
            try {
                cache.getReadWriteLock().writeLock().lock();
                Deque<ICommand> commandQueue = commands.get();
                while (!commandQueue.isEmpty()) {
                    ICommand command = commandQueue.removeLast();
                    undoCommands.get().push(command);
                    command.execute();
                }
            } catch (Throwable e) {
                Deque<ICommand> undoCommandQueue = undoCommands.get();
                while (!undoCommandQueue.isEmpty()) {
                    undoCommandQueue.pop().undo();
                }
                throw e;
            } finally {
                if (commands.get().isEmpty() || undoCommands.get().isEmpty()) {
                    commands.remove();
                    undoCommands.remove();
                }
                cache.getReadWriteLock().writeLock().unlock();
            }
        }
    }
}
