package com.jstore.order.expired;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 负责将prepare中的超时未处理任务回滚到store中。 PrepareQueue 中的 score = 秒级时间戳*1000 + ttl， 当 score < (当前时间 - 超时阈值)
 * 时认为该任务已超时，需要回滚到 WaitingQueue 重新消费。
 */
@Slf4j
@Component
public class JobTimeoutMonitor {
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RedisScript<Boolean> rollbackExpired;

    private final List<String> topics;
    private final TimerJobConfig timerJobConfig;

    public JobTimeoutMonitor(
            RedisTemplate<Object, Object> redisTemplate,
            @Qualifier("rollbackExpired") RedisScript<Boolean> rollbackExpired,
            TimerJobConfig timerJobConfig,
            List<TimerJobHandler> timerJobHandlers) {
        this.redisTemplate = redisTemplate;
        this.rollbackExpired = rollbackExpired;
        this.topics =
                timerJobHandlers.stream().map(TimerJobHandler::topic).collect(Collectors.toList());
        this.timerJobConfig = timerJobConfig;
    }

    @Scheduled(cron = "${timer.job.expire.cron: */5 * * * * ?}")
    public void findTimeOutJobAndRollItBackToWaitingQueue() {
        if (TimerJobCoordinator.stopped.get()) {
            return;
        }
        long tenSecondsBefore = System.currentTimeMillis() - (1000 * 10);
        for (String topic : topics) {
            for (int slot = 0; slot < timerJobConfig.getSlotAmount(); slot++) {
                try {
                    rollbackExpiredJobsInSlot(topic, slot, tenSecondsBefore);
                } catch (Exception e) {
                    log.error("回滚超时任务异常, topic={}, slot={}", topic, slot, e);
                }
            }
        }
    }

    private void rollbackExpiredJobsInSlot(String topic, int slot, long expireThreshold) {
        while (!TimerJobCoordinator.stopped.get()) {
            Boolean result =
                    redisTemplate.execute(
                            rollbackExpired,
                            Arrays.asList(
                                    TimerJobRepository.PrepareQueue.key(topic, slot),
                                    TimerJobRepository.WaitingQueue.key(topic, slot)),
                            expireThreshold);
            if (!result) {
                break;
            }
        }
    }
}
