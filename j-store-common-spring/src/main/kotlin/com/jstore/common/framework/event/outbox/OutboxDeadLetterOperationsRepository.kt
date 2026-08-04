package com.jstore.common.framework.event.outbox

import java.time.Instant

interface OutboxDeadLetterOperations {
    fun findDeadLetters(page: Int, size: Int): OutboxDeadLetterPage

    fun requeue(
        ids: Collection<String>,
        operatorId: String,
        reason: String,
        nextAttemptAt: Instant = Instant.now(),
    ): DeadLetterRequeueResult
}

/**
 * Atomic persistence boundary for dead-letter operations.
 *
 * Implementations must update an entry and append its audit record in the same transaction.
 */
interface OutboxDeadLetterOperationsRepository {
    fun findDeadLetters(page: Int, size: Int): OutboxDeadLetterPage

    fun requeueDeadLetters(
        ids: Collection<String>,
        operatorId: String,
        reason: String,
        nextAttemptAt: Instant,
    ): DeadLetterRequeueResult
}

data class OutboxDeadLetterPage(
    val entries: List<OutboxDeadLetterSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
)

/** Deliberately excludes payload. */
data class OutboxDeadLetterSummary(
    val id: String,
    val eventId: String,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retryCount: Int,
    val lastError: String?,
)

data class DeadLetterRequeueResult(
    val requeuedCount: Int,
    val notRequeuedCount: Int,
)

enum class OutboxDeadLetterAuditAction { REQUEUE }

enum class OutboxDeadLetterAuditResult { REQUEUED, NOT_REQUEUED }

data class OutboxDeadLetterAudit(
    val entryId: String,
    val eventId: String?,
    val operatorId: String,
    val action: OutboxDeadLetterAuditAction,
    val reason: String,
    val result: OutboxDeadLetterAuditResult,
    val createdAt: Instant,
)
