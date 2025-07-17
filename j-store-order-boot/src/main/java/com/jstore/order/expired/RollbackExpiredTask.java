package com.jstore.order.expired;




import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 负责将prepare中的超时未处理任务回滚到store中
 */
@Component
public abstract class RollbackExpiredTask implements Runnable {
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RedisScript<Boolean> rollbackExpired;

    private final List<String> topics;
    private final List<Integer> slots;

    public RollbackExpiredTask(
            RedisTemplate<Object, Object> redisTemplate,
            @Qualifier("rollbackExpired") RedisScript<Boolean> rollbackExpired,
            TimerJobConfig timerJobConfig,
            List<TimerJobHandler> timerJobHandlers
    ) {
        this.redisTemplate = redisTemplate;
        this.rollbackExpired = rollbackExpired;
        this.topics = timerJobHandlers.stream().map(TimerJobHandler::topic).collect(Collectors.toList());
        this.slots = Stream.iterate(0, i -> i + 1).limit(timerJobConfig.getSlotAmount()).collect(Collectors.toList());
    }

    private List<String> topics() {
        return topics;
    }
    private List<Integer> slots() {
       return slots;
    }

    @Scheduled(cron = "${timer.job.expire.cron: */5 * * * * ?}")
    public void scheduled() {
        run();
    }

    @Override
    public void run() {
        long thirtySecondsBefore = System.currentTimeMillis() - (1000 * 30);
        for (String topic : topics()) {
            for (Integer slot : slots()) {
                while (true) {
                    Boolean result = redisTemplate.execute(
                            rollbackExpired,
                            Arrays.asList(TimerJobRepository.WaitingQueue.key(topic, slot), TimerJobRepository.PrepareQueue.key(topic, slot)),
                            thirtySecondsBefore
                    );
                    if (!result) {
                        break;
                    }
                }
            }
        }
    }


}
