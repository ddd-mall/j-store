package com.jstore.common.framework.event.outbox

import java.time.Instant

/** Outbox 条目领域模型，表示一条待发布的领域事件记录。 */
data class OutboxEntry(
    val id: String,
    val eventType: String,
    val payload: String,
    val aggregateType: String,
    val aggregateId: String,
    val status: OutboxEntryStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retryCount: Int = 0,
    val nextAttemptAt: Instant = createdAt,
    val lockedBy: String? = null,
    val lockedAt: Instant? = null,
    val lockedUntil: Instant? = null,
    /** Monotonically increasing claim generation used to fence stale workers. */
    val lockToken: Long = 0,
    val lastError: String? = null,
    val eventId: String = id,
    val eventClassName: String = eventType,
    val eventVersion: Int = 1,
    val occurredAt: Instant = createdAt,
    val messageKind: OutboxMessageKind = OutboxMessageKind.DOMAIN_EVENT,
    val deliveryTarget: OutboxDeliveryTarget = OutboxDeliveryTarget.LOCAL_DOMAIN,
    val destination: String = eventType,
    val partitionKey: String = aggregateId,
    val correlationId: String = eventId,
    val causationId: String? = null,
    val tenantId: String? = null,
)

enum class OutboxMessageKind {
    DOMAIN_EVENT,
    INTEGRATION_EVENT,
    INTEGRATION_COMMAND,
}

enum class OutboxDeliveryTarget {
    LOCAL_DOMAIN,
    LOCAL_INTEGRATION,
    BROKER,
}
