package com.jstore.order.expired;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.jstore.order.expired.TimerJobConfig.EXPIRE_CENTER_POOL;


/**
 * 要负责给worker分配slot和topic及其对应的handler，并放入到线程池中执行，这里按理应该要使用zookeeper协调
 */
@Slf4j
@Component
public class JobDispatcher {

    private final ThreadPoolTaskExecutor executorService;
    private final TimerJobRepository jobRepository;
    private final TimerJobConfig timerJobConfig;
    private final Map<String, TimerJobHandler> handlers;
    private final List<String> topics;


    public JobDispatcher(
            TimerJobConfig timerJobConfig,
            @Qualifier(EXPIRE_CENTER_POOL) ThreadPoolTaskExecutor executorService,
            TimerJobRepository jobRepository,
            List<TimerJobHandler> handlers
    ) {
        this.timerJobConfig = timerJobConfig;
        this.executorService = executorService;
        this.jobRepository = jobRepository;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(TimerJobHandler::topic, handler -> handler));
        this.topics = handlers.stream().map(TimerJobHandler::topic).collect(Collectors.toList());
    }

    public void start() {
        Thread dispatcherThread = new Thread(() -> {
            while (!TimerJobCoordinator.stoped) {
                try {
                    dispatch();
                } catch (Exception e) {
                    log.info(e.getMessage());
                }
            }
        });
        dispatcherThread.setName("dispatcher");
        dispatcherThread.start();

    }

    private void dispatch() {
        for (AtomicInteger alows = new AtomicInteger(congestionControl()); alows.get() > 0; ) {
            for (String topic : topics) {
                for (int slot = 0; slot < timerJobConfig.getSlotAmount(); slot++) {
                    Integer finalSlot = slot;

                    if (TimerJobCoordinator.stoped) {
                        log.info("定时任务中心已停止，调度器将不再分配任务");
                        return;
                    }

                    // todo: 如果任务已经被取出，应该要在任务处理完成之前，阻塞Coordinator的stop
                    Optional<TimerJob> job = jobRepository.getOneJobFromWaitingQueue(topic, slot);
                    job.ifPresent(timerJob -> {
                        TimerJobCoordinator.handingJobs.incrementAndGet();
                        executorService.execute(() -> {
                            TimerJobCoordinator.lifeCycleLock.readLock().lock();
                            try {
                                new Worker(jobRepository, handlers).handle(timerJob, finalSlot);
                            } finally {
                                alows.getAndDecrement();
                                TimerJobCoordinator.lifeCycleLock.readLock().unlock();
                                TimerJobCoordinator.handingJobs.decrementAndGet();
                            }
                        });
                    });

                }
            }
        }
    }

    private int congestionControl() {
        return Math.max(executorService.getMaxPoolSize() - executorService.getActiveCount(), 0);
    }

}
