package com.jstore.common.framework.event.outbox

import org.slf4j.LoggerFactory
import java.time.Instant

class OutboxDeadLetterService(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val outboxMonitor: OutboxMonitor = NoopOutboxMonitor,
) {
    private val logger = LoggerFactory.getLogger(OutboxDeadLetterService::class.java)

    fun findDeadLetters(batchSize: Int): List<OutboxEntry> {
        return outboxEntryRepository.findDeadLetters(batchSize)
    }

    fun requeue(ids: Collection<String>, nextAttemptAt: Instant = Instant.now()): Int {
        val count = outboxEntryRepository.requeueDeadLetters(ids, nextAttemptAt)
        if (count > 0) {
            logger.warn("Outbox dead letters requeued: count={}, ids={}", count, ids)
            outboxMonitor.recordRequeue(count)
        }
        return count
    }

    fun countDeadLetters(): Long {
        return outboxEntryRepository.countByStatus(OutboxEntryStatus.DEAD_LETTER)
    }
}
