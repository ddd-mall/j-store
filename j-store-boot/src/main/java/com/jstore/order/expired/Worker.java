package com.jstore.order.expired;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Worker {

    private final TimerJobRepository jobRepository;
    private final Map<String, TimerJobHandler> handlers;

    public Worker(TimerJobRepository jobRepository, Map<String, TimerJobHandler> handlers) {
        this.jobRepository = jobRepository;
        this.handlers = handlers;
    }

    public void handle(TimerJob job, Integer slot) {
        if (null == job) return;
        // 如果ttl已经耗尽，标记为处理失败
        if (job.getTtl() <= 0) {
            try {
                jobRepository.markAsFailure(job, slot);
                log.warn("任务 {} ttl 耗尽", job.getId());
            } catch (Exception e) {
                log.error("Failed to mark job as failure during shutdown", e);
            }
            return;
        }

        TimerJobHandler timerJobHandler = handlers.get(job.getTopic());
        if (null == timerJobHandler) {
            log.error("未能发现topic: {} 对应的handler", job.getTopic());
            return;
        }
        try {
            boolean handleSuccess = timerJobHandler.handle(job);
            if (handleSuccess) {
                jobRepository.markAsHandled(job, slot);
            } else {
                jobRepository.rollbackOnFailure(job, slot);
            }
        } catch (Exception e) {
            log.error("定时任务处理过程发生异常", e);
            jobRepository.rollbackOnFailure(job, slot);
        }
    }
}
