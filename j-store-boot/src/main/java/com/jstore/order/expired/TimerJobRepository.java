package com.jstore.order.expired;


import com.jstore.common.errors.CommonErrors;
import com.jstore.common.utils.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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

    public TimerJobJpaPO addNewOneToDB(TimerJob timerJob) {
        TimerJobJpaPO timerJobJPAPO = new TimerJobJpaPO(timerJob, TimerJob.TimerJobStatus.UNHANDLED.name());
        return timerJobJAPRepository.save(timerJobJPAPO);
    }

    /**
     * 放入任务
     *
     * @param timerJob 任务
     * @param slot     放入的槽位
     */
    public void addOneJobToWaitingQueue(TimerJob timerJob, long slot) {
        redisTemplate.opsForZSet().add(WaitingQueue.key(timerJob.getTopic(), slot), TimerJob.toJsonStr(timerJob), timerJob.getExecuteTime().getTime());
    }


    /**
     * 从store queue中获取任务，并且将它移动到prepare queue中，
     * 如果是第一次被取出，则将ttl
     */
    private static final String emptyJsonStr = "";

    public Optional<TimerJob> getOneJobFromWaitingQueue(String topic, long slot) {
        long currentTimeStamp = System.currentTimeMillis();
        if (TimerJobCoordinator.stopped.get()) {
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
                List<TimerJobJpaPO> content = result.getContent();
                if (!content.isEmpty()) {
                    content.forEach(timerJob -> timerJob.setStatus(TimerJob.TimerJobStatus.HANDLING.name()));
                    content = timerJobJAPRepository.saveAllAndFlush(content);
                }
                hasNext = !(result.getContent().size() < batchSize);
                return content.stream().map(TimerJob::new).collect(Collectors.toList());
            }
        };
    }

    /**
     * roll back
     * TODO: 回滚这里的逻辑可能存在错误，需要进行排查，lua脚本中出现score为nil的情况
     *
     * @param job  回滚指定任务到waiting queue
     * @param slot 任务所属的槽/分组或者...随便
     */
    @Transactional(rollbackFor = Exception.class)
    public void rollbackOnFailure(TimerJob job, long slot) {
        RetryTimes.of(3)
                .doWithRetry(() ->
                        redisTemplate.execute(
                                rollbackTimerJob,
                                Arrays.asList(PrepareQueue.key(job.getTopic(), slot), WaitingQueue.key(job.getTopic(), slot)),
                                TimerJob.toJsonStr(job))
                ).whenException(exception -> log.error("回滚任务时发生异常，将进行重试: job={}, slot={}", job.getId(), slot, exception))
                .onFailure(() -> {
                    log.error("回滚任务 {} 失败", job);
                    markAsFailure(job, slot);
                });
    }


    @Transactional(rollbackFor = Exception.class)
    public void markAsHandled(TimerJob timerJob, long slot) {
        AtomicReference<TimerJobJpaPO> timerJobRef = new AtomicReference<>(null);
        RetryTimes.of(3)
                .doWithRetry(() -> {
                    timerJobJAPRepository.
                            findById(timerJob.getId())
                            .ifPresent(timerJobJAPPO -> {
                                timerJobRef.set(timerJobJAPPO);
                                timerJobJAPRepository.deleteById(timerJob.getId());
                            });
                    handledTimerJobJpaRepository.save(new HandledTimerJobJpaPO(timerJob));
                    removeFromPrepareQueue(timerJob, slot);
                }).whenException(exception -> log.error("标记任务为已处理过程发生异常", exception))
                .onFailure(() -> {
                    throw CommonErrors.INSTANCE.getINTERNAL_ERROR().msg(String.format("标记任务 %s 为已处理过程发生异常", JsonUtils.INSTANCE.toJsonString(timerJobRef.get())));
                });
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

    public List<TimerJob> updateStatus(List<TimerJob> timerJobs, String status) {
        List<Long> ids = timerJobs.stream().map(TimerJob::getId).toList();
        final int retryTimes = 3;
        AtomicReference<List<TimerJob>> timerJobRef = new AtomicReference<>(null);
        RetryTimes.of(retryTimes)
                .doWithRetry(() -> {
                    List<TimerJobJpaPO> pos = timerJobJAPRepository.findAllById(ids);
                    for (TimerJobJpaPO po : pos) {
                        po.setStatus(status);
                    }
                    List<TimerJob> list = timerJobJAPRepository.saveAll(pos).stream().map(TimerJob::new).toList();
                    timerJobRef.set(list);
                }).whenException(exception -> log.error("[任务状态更新]-更新任务状态时发生异常, 将进行重试", exception))
                .onFailure(() -> log.error("[任务状态更新]- 更新任务 {} 的状态为 {} 失败", JsonUtils.INSTANCE.toJsonString(ids), status));
        return timerJobRef.get();

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
