package com.jstore.order.expired;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;

/**
 * 负责将任务从数据库中取出并放置到redis中
 */
@Component
public class JobLoader {
    private final TimerJobRepository timerJobRepository;
    private final TimerJobConfig timerJobConfig;
    private final TimerJobRepository timerJobQueue;
    private final PlatformTransactionManager transactionManager;



    public JobLoader(TimerJobRepository timerJobRepository,
                     TimerJobConfig timerJobConfig,
                     TimerJobRepository jobRepository, PlatformTransactionManager transactionManager
    ) {
        this.timerJobRepository = timerJobRepository;
        this.timerJobConfig = timerJobConfig;
        this.timerJobQueue = jobRepository;

        this.transactionManager = transactionManager;
    }

    /**
     * 将未来10秒内要执行的任务从数据库中取出并加载到store queue中，并将其在库中的状态设置为 HANDLING
     */
    @Scheduled(cron = "${timer.job.producer.cron: */5 * * * * ?}")
    public void loadJobsFromDbToRedis() {
        if (TimerJobCoordinator.stoped.get()) {
            return;
        }
        AtomicBoolean acquired = new AtomicBoolean(false);
        try {
            acquired.set(
                    TimerJobCoordinator.lifeCycleLock.readLock().tryLock(300, TimeUnit.MILLISECONDS)
            );
        } catch (Exception ignore) {}
        if (!acquired.get()) {
            return;
        }

        try {
            long tenSecondsLater = System.currentTimeMillis() + 1000 * 10;
            Iterator<List<TimerJob>> iterator = timerJobRepository.getIteratorOfUnhandledAndBefore(new Date(tenSecondsLater), 100);
            while (iterator.hasNext() && !TimerJobCoordinator.stoped.get()) {
                DefaultTransactionDefinition transactionDefinition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                TransactionStatus transaction = transactionManager.getTransaction(transactionDefinition);
                try {
                    List<TimerJob> next = iterator.next();
                    next.forEach(timerJob -> {
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
