package com.jstore.order.expired;


import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Component
public class TimerJobCoordinator implements SmartLifecycle {
    public static final ReentrantReadWriteLock lifeCycleLock = new ReentrantReadWriteLock();
    public static final AtomicLong handlingJobs = new AtomicLong(0);
    public static final AtomicBoolean stopped = new AtomicBoolean(true);

    private final JobDispatcher jobDispatcher;
    private final SlotAssigner slotAssigner;


    public TimerJobCoordinator(JobDispatcher jobDispatcher, SlotAssigner slotAssigner) {
        this.jobDispatcher = jobDispatcher;
        this.slotAssigner = slotAssigner;
    }


    @Override
    public void start() {
        if (stopped.compareAndSet(true, false)) {
            jobDispatcher.start();
            log.info("定时任务中心已启动");
        } else {
            log.info("定时任务中心已经正在运行中");
        }

    }


    @Override
    public void stop() {
        if (!stopped.compareAndSet(false, true)) {
            log.info("定时任务中心已经是关闭状态");
        }
        log.info("定时任务中心正在关闭");

        // 优先释放 slot 锁，让其他实例尽快接管
        slotAssigner.releaseAll();

        lifeCycleLock.writeLock().lock();
        try {
            long startTime = System.currentTimeMillis();
            while (handlingJobs.get() > 0) {
                log.info("等待所有正在处理的任务完成，当前处理中的任务数: {}", handlingJobs.get());
                TimeUnit.MILLISECONDS.sleep(100);
                if (System.currentTimeMillis() - startTime > 30000) {
                    log.warn("等待任务处理超时，强制停止");
                    break;
                }
            }
        } catch (InterruptedException e) {
            log.error("coordinator 在等待任务处理完成时发生异常", e);
        } finally {
            lifeCycleLock.writeLock().unlock();
        }
        log.info("定时任务中心已关闭");
    }

    @Override
    public boolean isRunning() {
        return !stopped.get();
    }
}
