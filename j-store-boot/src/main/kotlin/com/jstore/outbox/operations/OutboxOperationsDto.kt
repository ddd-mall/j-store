package com.jstore.outbox.operations

import com.jstore.common.framework.event.outbox.DeadLetterRequeueResult
import com.jstore.common.framework.event.outbox.OutboxDeadLetterPage
import com.jstore.common.framework.event.outbox.OutboxDeadLetterSummary
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class RequeueDeadLettersRequest(
    @field:NotEmpty @field:Size(max = 100) val ids: List<@NotBlank String>,
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

data class DeadLetterPageResponse(
    val entries: List<DeadLetterSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    companion object {
        fun from(page: OutboxDeadLetterPage) =
            DeadLetterPageResponse(
                entries = page.entries.map(DeadLetterSummaryResponse::from),
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
            )
    }
}

/** Payload is intentionally absent from the operations API contract. */
data class DeadLetterSummaryResponse(
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
) {
    companion object {
        fun from(entry: OutboxDeadLetterSummary) =
            DeadLetterSummaryResponse(
                id = entry.id,
                eventId = entry.eventId,
                eventType = entry.eventType,
                aggregateType = entry.aggregateType,
                aggregateId = entry.aggregateId,
                eventVersion = entry.eventVersion,
                occurredAt = entry.occurredAt,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                retryCount = entry.retryCount,
                lastError = entry.lastError,
            )
    }
}

data class RequeueDeadLettersResponse(
    val requeuedCount: Int,
    val notRequeuedCount: Int,
) {
    companion object {
        fun from(result: DeadLetterRequeueResult) =
            RequeueDeadLettersResponse(result.requeuedCount, result.notRequeuedCount)
    }
}

data class OutboxOperationsErrorResponse(
    val message: String,
    val errorCode: String,
)
