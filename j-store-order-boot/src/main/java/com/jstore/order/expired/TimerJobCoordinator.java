package com.jstore.order.expired;


import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class TimerJobCoordinator implements SmartLifecycle {
    public static final ReentrantReadWriteLock lifeCycleLock = new ReentrantReadWriteLock();
    public static final AtomicLong handingJobs = new AtomicLong(0);
    public static volatile boolean stoped = true;

    private final JobDispatcher jobDispatcher;


    public TimerJobCoordinator(JobDispatcher jobDispatcher) {
        this.jobDispatcher = jobDispatcher;
    }


    @Override
    public void start() {
        stoped = false;
        jobDispatcher.start();
    }

    @Override
    public void stop() {
        stoped = true;
        lifeCycleLock.writeLock().lock();
        try {
            while (handingJobs.get() > 0) {
                log.info("等待所有正在处理的任务完成，当前处理中的任务数: {}", handingJobs.get());
                TimeUnit.MILLISECONDS.sleep(100);
            }
        } catch (InterruptedException e) {
            log.error("coordinator 在等待任务处理完成时发生异常", e);
        }
        lifeCycleLock.writeLock().unlock();
    }

    @Override
    public boolean isRunning() {
        return stoped;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("定时任务中心已启动");
        start();
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        log.info("定时任务中心正在关闭");
        stop();
        log.info("定时任务中心已关闭");
    }
}
