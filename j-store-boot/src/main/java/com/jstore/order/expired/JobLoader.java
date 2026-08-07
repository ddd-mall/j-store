/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.order.expired;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/** 负责将任务从数据库中取出并放置到redis中 */
@Component
@Slf4j
public class JobLoader {
    private final TimerJobRepository timerJobRepository;
    private final TimerJobConfig timerJobConfig;
    private final TimerJobRepository timerJobQueue;
    private final PlatformTransactionManager transactionManager;

    public JobLoader(
            TimerJobRepository timerJobRepository,
            TimerJobConfig timerJobConfig,
            TimerJobRepository jobRepository,
            PlatformTransactionManager transactionManager) {
        this.timerJobRepository = timerJobRepository;
        this.timerJobConfig = timerJobConfig;
        this.timerJobQueue = jobRepository;
        this.transactionManager = transactionManager;
    }

    /** 将未来10秒内要执行的任务从数据库中取出并加载到store queue中，并将其在库中的状态设置为 HANDLING */
    @Scheduled(cron = "${timer.job.producer.cron: */5 * * * * ?}")
    public void loadJobsFromDbToRedis() {
        if (TimerJobCoordinator.stopped.get()) {
            return;
        }
        AtomicBoolean acquired = new AtomicBoolean(false);
        try {
            acquired.set(
                    TimerJobCoordinator.lifeCycleLock
                            .readLock()
                            .tryLock(300, TimeUnit.MILLISECONDS));
        } catch (Exception ignore) {
        }
        if (!acquired.get()) {
            return;
        }

        try {
            long tenSecondsLater = System.currentTimeMillis() + 1000 * 10;
            Iterator<List<TimerJob>> iterator =
                    timerJobRepository.getIteratorOfUnhandledAndBefore(
                            new Date(tenSecondsLater), 100);
            while (iterator.hasNext() && !TimerJobCoordinator.stopped.get()) {

                DefaultTransactionDefinition transactionDefinition =
                        new DefaultTransactionDefinition(
                                TransactionDefinition.PROPAGATION_REQUIRED);
                TransactionStatus transaction =
                        transactionManager.getTransaction(transactionDefinition);
                try {
                    List<TimerJob> next = iterator.next();
                    next.forEach(
                            timerJob -> {
                                long slot = slot(timerJob);
                                timerJobQueue.addOneJobToWaitingQueue(timerJob, slot);
                            });
                    transactionManager.commit(transaction);
                } catch (Exception e) {
                    transactionManager.rollback(transaction);
                }
            }
        } finally {
            if (acquired.get()) {
                TimerJobCoordinator.lifeCycleLock.readLock().unlock();
            }
        }
    }

    /**
     * 补偿扫描：将 HANDLING 状态但已超时的任务重新加载到 Redis。
     *
     * <p>正常流程中，任务通过 addNewJobAndEnqueue 创建时 DB 状态直接设为 HANDLING， 同时写入 Redis WaitingQueue。如果 Redis
     * 故障导致数据丢失，这些任务会卡在 HANDLING 状态，既不在 Redis 中，也不会被 loadJobsFromDbToRedis 扫描到 （因为它只扫 UNHANDLED）。
     *
     * <p>补偿逻辑：扫描 status=HANDLING 且 executeTime 已过期超过 60 秒的任务。 60
     * 秒的阈值是为了避免误伤刚创建的正常任务（正常任务从创建到消费完成通常在秒级）。 将这些任务重新放入 Redis WaitingQueue，让调度流程重新接管。
     */
    @Scheduled(cron = "${timer.job.compensate.cron: 0 */1 * * * ?}")
    public void compensateStuckHandlingJobs() {
        if (TimerJobCoordinator.stopped.get()) {
            return;
        }
        AtomicBoolean acquired = new AtomicBoolean(false);
        try {
            acquired.set(
                    TimerJobCoordinator.lifeCycleLock
                            .readLock()
                            .tryLock(300, TimeUnit.MILLISECONDS));
        } catch (Exception ignore) {
        }
        if (!acquired.get()) {
            return;
        }

        try {
            // 只补偿 executeTime 已过期超过 60 秒的 HANDLING 任务
            long sixtySecondsAgo = System.currentTimeMillis() - 60_000;
            Iterator<List<TimerJob>> iterator =
                    timerJobRepository.getIteratorOfUnhandledAndBefore(
                            new Date(sixtySecondsAgo),
                            TimerJob.TimerJobStatus.HANDLING.name(),
                            100);

            int compensatedCount = 0;
            while (iterator.hasNext() && !TimerJobCoordinator.stopped.get()) {
                DefaultTransactionDefinition txDef =
                        new DefaultTransactionDefinition(
                                TransactionDefinition.PROPAGATION_REQUIRED);
                TransactionStatus transaction = transactionManager.getTransaction(txDef);
                try {
                    List<TimerJob> batch = iterator.next();
                    batch.forEach(
                            timerJob -> {
                                long slot = slot(timerJob);
                                timerJobQueue.addOneJobToWaitingQueue(timerJob, slot);
                            });
                    transactionManager.commit(transaction);
                    compensatedCount += batch.size();
                } catch (Exception e) {
                    transactionManager.rollback(transaction);
                    log.error("补偿扫描批次处理失败", e);
                }
            }

            if (compensatedCount > 0) {
                log.info("补偿扫描完成，共重新加载 {} 个卡在 HANDLING 状态的任务", compensatedCount);
            }
        } finally {
            if (acquired.get()) {
                TimerJobCoordinator.lifeCycleLock.readLock().unlock();
            }
        }
    }

    /**
     * 计算任务的槽位（其实就是一个散列算法，希望任务均匀地分布在各个槽中）
     *
     * @param job 任务
     * @return 任务所属地槽
     */
    public long slot(TimerJob job) {
        long slotAmount = timerJobConfig.getSlotAmount();
        // 计算slotLen所需的最少字节数

        int bytesNeeded = minBytesForValue(slotAmount);
        int bitsNeeded = bytesNeeded * 8;

        // 将32位CRC值分段，然后异或
        long crcValue = crc32(job);
        long result = 0;

        for (int i = 0; i < 32; i += bitsNeeded) {
            result ^= (crcValue >> i) & ((1L << bitsNeeded) - 1);
        }
        // 返回结果对slotLen取模
        return result % slotAmount;
    }

    public long crc32(TimerJob timerJob) {
        // 计算CRC32值
        final CRC32 crc32 = new CRC32();
        crc32.update(TimerJob.toJsonStr(timerJob).getBytes());
        return crc32.getValue();
    }

    private static int minBytesForValue(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be positive");
        }

        if (value <= 0xFF) return 1;
        if (value <= 0xFFFF) return 2;
        if (value <= 0xFFFFFF) return 3;
        if (value <= 0xFFFFFFFFL) return 4;
        return 8; // 对于非常大的值，使用8字节
    }
}
