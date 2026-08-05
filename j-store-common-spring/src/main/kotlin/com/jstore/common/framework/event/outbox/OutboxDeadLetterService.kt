package com.jstore.common.framework.event.outbox

import java.time.Instant
import org.slf4j.LoggerFactory

class OutboxDeadLetterService(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val outboxMonitor: OutboxMonitor = NoopOutboxMonitor,
) : OutboxDeadLetterOperations {
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

    override fun findDeadLetters(page: Int, size: Int): OutboxDeadLetterPage {
        require(page >= 1) { "page must be at least 1" }
        require(size in 1..200) { "size must be between 1 and 200" }
        return operationsRepository().findDeadLetters(page, size)
    }

    override fun requeue(
        ids: Collection<String>,
        operatorId: String,
        reason: String,
        nextAttemptAt: Instant,
    ): DeadLetterRequeueResult {
        require(ids.isNotEmpty()) { "ids must not be empty" }
        require(ids.size <= 100) { "at most 100 dead letters can be requeued at once" }
        require(operatorId.isNotBlank()) { "operatorId must not be blank" }
        require(reason.isNotBlank()) { "reason must not be blank" }

        val result =
            operationsRepository()
                .requeueDeadLetters(
                    ids = ids.toList().distinct(),
                    operatorId = operatorId,
                    reason = reason.trim(),
                    nextAttemptAt = nextAttemptAt,
                )
        if (result.requeuedCount > 0) {
            logger.warn(
                "Outbox dead letters requeued: count={}, requestedCount={}, operatorId={}",
                result.requeuedCount,
                ids.size,
                operatorId,
            )
            outboxMonitor.recordRequeue(result.requeuedCount)
        }
        return result
    }

    fun countDeadLetters(): Long {
        return outboxEntryRepository.countByStatus(OutboxEntryStatus.DEAD_LETTER)
    }

    private fun operationsRepository(): OutboxDeadLetterOperationsRepository {
        return outboxEntryRepository as? OutboxDeadLetterOperationsRepository
            ?: error("Outbox repository does not provide production dead-letter operations")
    }
}
