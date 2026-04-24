package com.jstore.order.expired;


import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 负责将prepare中的超时未处理任务回滚到store中
 */
@Component
public abstract class JobTimeoutMonitor implements Runnable {
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RedisScript<Boolean> rollbackExpired;

    private final List<String> topics;
    private final TimerJobConfig timerJobConfig;

    public JobTimeoutMonitor(
            RedisTemplate<Object, Object> redisTemplate,
            @Qualifier("rollbackExpired") RedisScript<Boolean> rollbackExpired,
            TimerJobConfig timerJobConfig,
            List<TimerJobHandler> timerJobHandlers
    ) {
        this.redisTemplate = redisTemplate;
        this.rollbackExpired = rollbackExpired;
        this.topics = timerJobHandlers.stream().map(TimerJobHandler::topic).collect(Collectors.toList());
        this.timerJobConfig = timerJobConfig;
    }

    private List<String> topics() {
        return topics;
    }


    @Scheduled(cron = "${timer.job.expire.cron: */5 * * * * ?}")
    public void findTimeOutJobAndRollItBackToWaitingQueue() {
        run();
    }

    @Override
    public void run() {
        long tenSecondsBefore = System.currentTimeMillis() - (1000 * 10);
        for (String topic : topics()) {
            for (int slot = 0; slot < timerJobConfig.getSlotAmount(); slot++) {
                while (true) {
                    Boolean result = redisTemplate.execute(
                            rollbackExpired,
                            Arrays.asList(TimerJobRepository.PrepareQueue.key(topic, slot), TimerJobRepository.WaitingQueue.key(topic, slot)),
                            tenSecondsBefore
                    );
                    if (!result) {
                        break;
                    }
                }
            }
        }
    }


}
