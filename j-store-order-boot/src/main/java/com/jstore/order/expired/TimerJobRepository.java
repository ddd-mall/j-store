package com.jstore.order.expired;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Repository
public class TimerJobRepository {
    private final TimerJobPODAO timerJobPODAO;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RedisScript<List> moveToPrepare;
    private final RedisScript<Boolean> rollback;

    public TimerJobRepository(
            TimerJobPODAO timerJobPODAO,
            RedisTemplate<Object, Object> redisTemplate,
            @Qualifier("moveToPrepare") RedisScript<List> moveToPrepare,
            @Qualifier("rollback") RedisScript<Boolean> rollback
    ) {
        this.timerJobPODAO = timerJobPODAO;
        this.redisTemplate = redisTemplate;
        this.moveToPrepare = moveToPrepare;
        this.rollback = rollback;
    }

    public boolean addNewOneToDB(TimerJob timerJob) {
        TimerJobPO timerJobPO = new TimerJobPO();
        timerJobPO.setTopic(timerJob.getTopic());
        timerJobPO.setContent(timerJob.getContent());
        timerJobPO.setExecuteTime(timerJob.getExecuteTime());
        timerJobPO.setStatus(TimerJob.TimerJobStatus.UNHANDLED.name());
        return timerJobPODAO.saveOrUpdate(timerJobPO);
    }

    /**
     * 放入任务
     *
     * @param timerJob 任务
     * @param slot     放入的槽位
     */
    public void addOneJobToWaitingQueue(TimerJob timerJob, long slot) {
        redisTemplate.opsForZSet().add(WaitingQueue.key(timerJob.getTopic(), slot), TimerJob.toJsonStr(timerJob), timerJob.getExecuteTime().getTime());
        log.debug("将任务放置到store queue");
    }


    /**
     * 从store queue中获取任务，并且将它移动到prepare queue中，
     * 如果是第一次被取出，则将ttl
     */
    private static final String emptyJsonStr = "";

    public Optional<TimerJob> getOneJobFromWaitingQueue(String topic, long slot) {
        long currentTimeStamp = System.currentTimeMillis();
        if (!TimerJobCoordinator.lifeCycleLock.readLock().tryLock() || TimerJobCoordinator.stoped) {
            return Optional.empty();
        }
        List<Object> result;
        try {
            result = redisTemplate.execute(
                    moveToPrepare,
                    Arrays.asList(WaitingQueue.key(topic, slot), PrepareQueue.key(topic, slot)),
                    currentTimeStamp
            );
        } catch (Exception e) {
            log.warn("Failed to execute Redis script, possibly during shutdown: {}", e.getMessage());
            return Optional.empty();
        } finally {
            TimerJobCoordinator.lifeCycleLock.readLock().unlock();
        }

        // 检查返回结果的有效性 - 修复null检查
        if (result.isEmpty() || result.size() < 2) {
            return Optional.empty();
        }

        log.debug("从store queue中获取");
        String timerJobJsonStr = (String) (null == result.get(0) ? emptyJsonStr : result.get(0));
        TimerJob timerJob = TimerJob.fromJsonStr(timerJobJsonStr);

        // 安全地处理ttl值
        Object ttlObj = result.get(1);
        timerJob.setTtl(ttlToNumber(ttlObj));
        return Optional.of(timerJob);
    }

    private long ttlToNumber(Object ttlObj) {
        long ttl = 16; // 默认值
        if (ttlObj != null) {
            if (ttlObj instanceof Number) {
                ttl = ((Number) ttlObj).longValue();
            } else if (ttlObj instanceof String) {
                try {
                    ttl = Long.parseLong((String) ttlObj);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse TTL value: {}, using default value 16", ttlObj);
                }
            }
        }
        return ttl;
    }



    public Iterator<List<TimerJob>> getIteratorOfUnhandledAndBefore(Date date, int batchSize) {
        return timerJobPODAO.getIteratorOfUnhandledAndBefore(date, batchSize);
    }

    /**
     * roll back
     *
     * @param job  回滚指定任务到waiting queue
     * @param slot 任务所属的槽/分组或者...随便
     */
    public void rollbackOnFailure(TimerJob job, long slot) {
        redisTemplate.execute(
                rollback,
                Arrays.asList(PrepareQueue.key(job.getTopic(), slot), WaitingQueue.key(job.getTopic(), slot)),
                TimerJob.toJsonStr(job)
        );
    }


    @Transactional(rollbackFor = Exception.class)
    public void markAsHandled(TimerJob timerJob, long slot) {
        removeFromPrepareQueue(timerJob, slot);
        timerJobPODAO.markAsHandled(timerJob);
    }


    private void removeFromPrepareQueue(TimerJob timerJob, long slot) {
        redisTemplate.opsForZSet().remove(PrepareQueue.key(timerJob.getTopic(), slot), TimerJob.toJsonStr(timerJob));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsFailure(TimerJob timerJob, long slot) {
        markAsHandled(timerJob, slot);
        addJobToDeadQueue(timerJob);
    }

    private void addJobToDeadQueue(TimerJob timerJob) {
        // todo: 实现死信队列
    }



    public static class WaitingQueue {
        public static String key(String topic, long slot) {
            return  String.join("_", "waiting", hashTag(slot), topic);
        }
    }

    public static class PrepareQueue {
        public static String key(String topic, long slot) {
            return String.join("_", "prepare", hashTag(slot), topic);
        }
    }

    private static String hashTag(long slot) {
        return "{" + slot + "}";
    }







}
