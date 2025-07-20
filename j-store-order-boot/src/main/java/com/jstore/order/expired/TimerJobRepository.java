package com.jstore.order.expired;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Repository
@SuppressWarnings({"rawtypes", "unchecked"})
public class TimerJobRepository {
    private final TimerJobJpaRepository timerJobJAPRepository;
    private final HandledTimerJobJpaRepository handledTimerJobJpaRepository;
    private final TimerJobDeadQueueJpaRepository timerJobDeadQueueJpaRepository;
    private final RedisTemplate<Object, Object> redisTemplate;
    private final RedisScript<List> pickOneOutAndPrepare;
    private final RedisScript<Boolean> rollbackTimerJob;

    public TimerJobRepository(
            TimerJobJpaRepository timerJobJAPRepository,
            HandledTimerJobJpaRepository handledTimerJobJpaRepository,
            TimerJobDeadQueueJpaRepository timerJobDeadQueueJpaRepository,
            RedisTemplate<Object, Object> redisTemplate,
            @Qualifier("pickOneOutAndPrepare") RedisScript<List> pickOneOutAndPrepare,
            @Qualifier("rollbackTimerJob") RedisScript<Boolean> rollbackTimerJob
    ) {

        this.timerJobJAPRepository = timerJobJAPRepository;
        this.handledTimerJobJpaRepository = handledTimerJobJpaRepository;
        this.timerJobDeadQueueJpaRepository = timerJobDeadQueueJpaRepository;
        this.redisTemplate = redisTemplate;
        this.pickOneOutAndPrepare = pickOneOutAndPrepare;
        this.rollbackTimerJob = rollbackTimerJob;
    }

    public boolean addNewOneToDB(TimerJob timerJob) {
        TimerJobJpaPO timerJobJPAPO = new TimerJobJpaPO(timerJob, TimerJob.TimerJobStatus.UNHANDLED.name());
        timerJobJAPRepository.save(timerJobJPAPO);
        return true;
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
        if (TimerJobCoordinator.stoped.get()) {
            return Optional.empty();
        }
        List<Object> result;
        try {
            result = redisTemplate.execute(
                    pickOneOutAndPrepare,
                    Arrays.asList(WaitingQueue.key(topic, slot), PrepareQueue.key(topic, slot)),
                    currentTimeStamp
            );
        } catch (Exception e) {
            log.warn("Failed to execute Redis script, possibly during shutdown: {}", e.getMessage());
            return Optional.empty();
        }

        // 检查返回结果的有效性 - 修复null检查
        if (result.isEmpty() || result.size() < 2) {
            return Optional.empty();
        }

        log.debug("从store queue中获取");
        String timerJobJsonStr = (String) (null == result.get(0) ? emptyJsonStr : result.get(0));
        TimerJob timerJob = TimerJob.fromJsonStr(timerJobJsonStr);


        Object ttlObj = result.get(1);
        timerJob.setTtl(ttlToNumber(ttlObj));
        return Optional.of(timerJob);
    }

    private int ttlToNumber(Object ttlObj) {
        int ttl = 16; // 默认值
        if (ttlObj != null) {
            if (ttlObj instanceof Number) {
                ttl = ((Number) ttlObj).intValue();
            } else if (ttlObj instanceof String) {
                try {
                    ttl = Integer.parseInt((String) ttlObj);
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse TTL value: {}, using default value 16", ttlObj);
                }
            }
        }
        return ttl;
    }


    public Iterator<List<TimerJob>> getIteratorOfUnhandledAndBefore(Date date, int batchSize) {
        return new Iterator<>() {
            boolean hasNext = true;

            @Override
            public boolean hasNext() {
                return hasNext;
            }

            @Override
            @Transactional(rollbackFor = Exception.class)
            public List<TimerJob> next() {
                Page<TimerJobJpaPO> result = timerJobJAPRepository.findAllByExecuteTimeBeforeAndStatus(
                        date,
                        TimerJob.TimerJobStatus.UNHANDLED.name(),
                        Pageable.ofSize(batchSize)
                );
                if (!result.getContent().isEmpty()) {
                    List<Long> ids = result.getContent().stream().map(TimerJobJpaPO::getId).collect(Collectors.toList());
                    timerJobJAPRepository.updateStatusToHandlingByIds(ids, TimerJob.TimerJobStatus.HANDLING.name());
                }
                hasNext = !(result.getContent().size() < batchSize);
                return result.get().map(TimerJob::new).collect(Collectors.toList());
            }
        };
    }

    /**
     * roll back
     *
     * @param job  回滚指定任务到waiting queue
     * @param slot 任务所属的槽/分组或者...随便
     */
    public void rollbackOnFailure(TimerJob job, long slot) {
        RetryTimes.of(3)
                .doWithRetry(() -> redisTemplate.execute(
                        rollbackTimerJob,
                        Arrays.asList(PrepareQueue.key(job.getTopic(), slot), WaitingQueue.key(job.getTopic(), slot)),
                        TimerJob.toJsonStr(job))
                )
                .whenException(exception -> log.warn("回滚任务时发生异常，将进行重试: job={}, slot={}, error={}", job.getId(), slot, exception.getMessage()))
                .onFailure(() -> {
                    log.error("Failed to execute Redis script after retries, possibly during shutdown: {}", job);
                    addJobToDeadQueue(job);
                });
    }


    @Transactional(rollbackFor = Exception.class)
    public void markAsHandled(TimerJob timerJob, long slot) {
        timerJobJAPRepository.
                findById(timerJob.getId())
                .ifPresent(timerJobJAPPO -> timerJobJAPRepository.deleteById(timerJob.getId()));
        handledTimerJobJpaRepository.save(new HandledTimerJobJpaPO(timerJob));
        removeFromPrepareQueue(timerJob, slot);
    }


    private void removeFromPrepareQueue(TimerJob timerJob, long slot) {
        redisTemplate.opsForZSet().remove(PrepareQueue.key(timerJob.getTopic(), slot), TimerJob.toJsonStr(timerJob));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAsFailure(TimerJob timerJob, long slot) {
        timerJobJAPRepository
                .findById(timerJob.getId())
                .ifPresent(timerJobJAPPO -> timerJobJAPRepository.deleteById(timerJob.getId()));
        addJobToDeadQueue(timerJob);
        removeFromPrepareQueue(timerJob, slot);
    }

    private void addJobToDeadQueue(TimerJob timerJob) {
        TimerJobDeadQueueJpaPO timerJobDeadQueueJpaPO = new TimerJobDeadQueueJpaPO(timerJob);
        timerJobDeadQueueJpaRepository.save(timerJobDeadQueueJpaPO);
    }


    public static class WaitingQueue {
        public static String key(String topic, long slot) {
            return TimerJobConfig.JOB_KEY_PREFIX + String.join("_", "waiting", hashTag(slot), topic);
        }
    }

    public static class PrepareQueue {
        public static String key(String topic, long slot) {
            return TimerJobConfig.JOB_KEY_PREFIX + String.join("_", "prepare", hashTag(slot), topic);
        }
    }

    private static String hashTag(long slot) {
        return "{" + slot + "}";
    }
}
