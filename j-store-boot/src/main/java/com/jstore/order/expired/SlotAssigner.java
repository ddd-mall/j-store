package com.jstore.order.expired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的分布式 Slot 分配器。
 *
 * <p>每个实例尝试对每个 slot 加锁（带 TTL），成功则拥有该 slot 的消费权。 通过定时续约保持锁的持有，实例宕机后锁自动过期，其他实例可以接管。
 *
 * <p>这替代了原文中 ZooKeeper 的角色，用更轻量的方式实现 slot 独占消费。
 */
@Slf4j
@Component
public class SlotAssigner {

    private static final String SLOT_LOCK_PREFIX = "timer:job:slot:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);

    private final String instanceId = UUID.randomUUID().toString();
    private final RedisTemplate<Object, Object> redisTemplate;
    private final TimerJobConfig timerJobConfig;

    /** 当前实例持有的 slot 列表，由 refreshSlotAssignment 定时刷新 -- GETTER -- 获取当前实例拥有的 slot 列表 */
    @Getter private volatile List<Integer> ownedSlots = Collections.emptyList();

    public SlotAssigner(
            RedisTemplate<Object, Object> redisTemplate, TimerJobConfig timerJobConfig) {
        this.redisTemplate = redisTemplate;
        this.timerJobConfig = timerJobConfig;
    }

    public String getInstanceId() {
        return instanceId;
    }

    /** 定时刷新 slot 分配：尝试获取未被持有的 slot，续约已持有的 slot。 每 10 秒执行一次，锁 TTL 30 秒，保证宕机后最多 30 秒其他实例可接管。 */
    @Scheduled(fixedDelay = 10_000, initialDelay = 1_000)
    public void refreshSlotAssignment() {
        if (TimerJobCoordinator.stopped.get()) {
            return;
        }

        int totalSlots = timerJobConfig.getSlotAmount();
        List<Integer> acquired = new ArrayList<>();

        for (int slot = 0; slot < totalSlots; slot++) {
            String lockKey = SLOT_LOCK_PREFIX + slot;
            try {
                // 尝试获取或续约
                Boolean success =
                        redisTemplate.opsForValue().setIfAbsent(lockKey, instanceId, LOCK_TTL);

                if (Boolean.TRUE.equals(success)) {
                    // 新获取到的 slot
                    acquired.add(slot);
                } else {
                    // 检查是否是自己持有的，如果是则续约
                    Object holder = redisTemplate.opsForValue().get(lockKey);
                    if (instanceId.equals(holder)) {
                        redisTemplate.expire(lockKey, LOCK_TTL);
                        acquired.add(slot);
                    }
                }
            } catch (Exception e) {
                log.warn("Slot {} 分配/续约失败: {}", slot, e.getMessage());
            }
        }

        this.ownedSlots = Collections.unmodifiableList(acquired);

        if (log.isDebugEnabled()) {
            log.debug("实例 {} 当前持有 slots: {}", instanceId, ownedSlots);
        }
    }

    /** 释放当前实例持有的所有 slot 锁（优雅关闭时调用） */
    public void releaseAll() {
        for (int slot : ownedSlots) {
            String lockKey = SLOT_LOCK_PREFIX + slot;
            try {
                Object holder = redisTemplate.opsForValue().get(lockKey);
                if (instanceId.equals(holder)) {
                    redisTemplate.delete(lockKey);
                }
            } catch (Exception e) {
                log.warn("释放 slot {} 锁失败: {}", slot, e.getMessage());
            }
        }
        this.ownedSlots = Collections.emptyList();
        log.info("实例 {} 已释放所有 slot 锁", instanceId);
    }
}
