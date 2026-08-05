package com.jstore.order.expired;

import static com.jstore.order.expired.TimerJobConfig.EXPIRE_CENTER_POOL;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/**
 * 负责给worker分配slot和topic及其对应的handler，并放入到线程池中执行。 通过 SlotAssigner 实现分布式 slot 分配，每个实例只消费自己持有的 slot。
 */
@Slf4j
@Component
public class JobDispatcher {

    private final ThreadPoolTaskExecutor executorService;
    private final TimerJobRepository jobRepository;
    private final SlotAssigner slotAssigner;
    private final Map<String, TimerJobHandler> handlers;
    private final List<String> topics;

    /** 无任务时的空闲等待时间（毫秒），避免 CPU 空转 */
    private static final long IDLE_SLEEP_MS = 50;

    /** 一轮 dispatch 完成后的短暂让步时间（毫秒） */
    private static final long YIELD_SLEEP_MS = 5;

    public JobDispatcher(
            @Qualifier(EXPIRE_CENTER_POOL) ThreadPoolTaskExecutor executorService,
            TimerJobRepository jobRepository,
            SlotAssigner slotAssigner,
            List<TimerJobHandler> handlers) {
        this.executorService = executorService;
        this.jobRepository = jobRepository;
        this.slotAssigner = slotAssigner;
        this.handlers =
                handlers.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        TimerJobHandler::topic, handler -> handler));
        this.topics = handlers.stream().map(TimerJobHandler::topic).collect(Collectors.toList());
    }

    public void start() {
        Thread dispatcherThread =
                new Thread(
                        () -> {
                            while (!TimerJobCoordinator.stopped.get()) {
                                try {
                                    dispatch();
                                } catch (Exception e) {
                                    log.info(e.getMessage());
                                }
                                try {
                                    Thread.sleep(YIELD_SLEEP_MS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            }
                        });
        dispatcherThread.setName("dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
        log.info("定时任务中心任务调度器已启动");
    }

    private void dispatch() {
        List<Integer> mySlots = slotAssigner.getOwnedSlots();
        if (mySlots.isEmpty()) {
            try {
                Thread.sleep(IDLE_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }

        boolean dispatched = false;
        for (int allows = congestionControl(); allows > 0; ) {
            for (String topic : topics) {
                for (int slot : mySlots) {
                    if (allows <= 0) break;

                    Optional<TimerJob> job = Optional.empty();
                    TimerJobCoordinator.lifeCycleLock.readLock().lock();
                    try {
                        if (TimerJobCoordinator.stopped.get()) {
                            log.info("定时任务中心-调度器已停止，将不再分配任务");
                            return;
                        }
                        job = jobRepository.getOneJobFromWaitingQueue(topic, slot);
                        final int finalSlot = slot;
                        job.ifPresent(
                                timerJob ->
                                        executorService.execute(
                                                () -> {
                                                    try {
                                                        TimerJobCoordinator.handlingJobs
                                                                .incrementAndGet();
                                                        new Worker(jobRepository, handlers)
                                                                .handle(timerJob, finalSlot);
                                                    } finally {
                                                        TimerJobCoordinator.handlingJobs
                                                                .decrementAndGet();
                                                    }
                                                }));
                        if (job.isPresent()) {
                            dispatched = true;
                        }
                    } catch (Exception e) {
                        if (job.isPresent()) {
                            log.error(
                                    "Failed to dispatch job: {} in topic: {} at slot: {}",
                                    job.get(),
                                    topic,
                                    slot,
                                    e);
                            jobRepository.rollbackOnFailure(job.get(), slot);
                        } else {
                            log.warn(
                                    "Failed to dispatch job in topic: {} at slot: {}, possibly during shutdown: {}",
                                    topic,
                                    slot,
                                    e.getMessage());
                        }
                    } finally {
                        allows--;
                        TimerJobCoordinator.lifeCycleLock.readLock().unlock();
                    }
                }
            }
        }

        // 如果本轮没有 dispatch 任何任务，短暂休眠避免空转
        if (!dispatched) {
            try {
                Thread.sleep(IDLE_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private int congestionControl() {
        try {
            // 获取线程池的核心配置信息
            int maxPoolSize = executorService.getMaxPoolSize();
            int activeCount = executorService.getActiveCount();
            int poolSize = executorService.getPoolSize();

            // 获取队列大小信息
            int queueSize = executorService.getThreadPoolExecutor().getQueue().size();
            int queueCapacity =
                    executorService.getThreadPoolExecutor().getQueue().remainingCapacity();

            // 计算真正可用的容量
            // 如果队列有剩余容量，即使活跃线程数达到最大值，任务仍可能被接受
            int availableCapacity = Math.max(maxPoolSize - activeCount, 0) + queueCapacity;

            // 为了安全起见，保留一定的缓冲区，避免边界情况
            int safeCapacity = Math.max(availableCapacity - 2, 0);

            // 记录详细信息用于调试
            if (log.isDebugEnabled()) {
                log.debug(
                        "线程池状态 - 最大线程数: {}, 当前活跃线程: {}, 当前线程池大小: {}, "
                                + "队列中任务数: {}, 队列剩余容量: {}, 计算可用容量: {}, 安全容量: {}",
                        maxPoolSize,
                        activeCount,
                        poolSize,
                        queueSize,
                        queueCapacity,
                        availableCapacity,
                        safeCapacity);
            }

            // 如果线程池接近饱和，额外检查线程池状态
            if (safeCapacity <= 1) {
                // 检查线程池是否正在关闭
                if (executorService.getThreadPoolExecutor().isShutdown()
                        || executorService.getThreadPoolExecutor().isTerminating()) {
                    log.warn("线程池正在关闭，停止分配新任务");
                    return 0;
                }

                return 0;
            }
            return safeCapacity;

        } catch (Exception e) {
            // 如果获取线程池状态时出现异常，采用保守策略
            log.error("获取线程池状态时发生异常，采用保守的拥塞控制策略", e);
            return 0;
        }
    }
}
