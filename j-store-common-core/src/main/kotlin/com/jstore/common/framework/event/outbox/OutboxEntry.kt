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
) {
    init {
        require(id.isNotBlank()) { "Outbox entry ID must not be blank" }
        require(eventId.isNotBlank()) { "Outbox event/message ID must not be blank" }
        require(eventType.isNotBlank()) { "Outbox event/message type must not be blank" }
        require(eventClassName.isNotBlank()) { "Outbox class name must not be blank" }
        require(eventVersion > 0) { "Outbox event/message version must be positive" }
        require(aggregateType.isNotBlank()) { "Outbox aggregate type must not be blank" }
        require(aggregateId.isNotBlank()) { "Outbox aggregate ID must not be blank" }
        require(destination.isNotBlank()) { "Outbox destination must not be blank" }
        require(partitionKey.isNotBlank()) { "Outbox partition key must not be blank" }
        require(correlationId.isNotBlank()) { "Outbox correlation ID must not be blank" }
        require(retryCount >= 0) { "Outbox retry count must not be negative" }
        require(lockToken >= 0) { "Outbox lock token must not be negative" }

        val hasCompleteLease = lockedBy != null && lockedAt != null && lockedUntil != null
        if (status == OutboxEntryStatus.IN_PROGRESS) {
            require(hasCompleteLease) { "IN_PROGRESS outbox entry requires a complete lease" }
            require(lockedUntil.isAfter(lockedAt)) {
                "Outbox lease expiry must be after its acquisition time"
            }
        } else {
            require(lockedBy == null && lockedAt == null && lockedUntil == null) {
                "Only IN_PROGRESS outbox entries may hold a lease"
            }
        }

        when (messageKind) {
            OutboxMessageKind.DOMAIN_EVENT ->
                require(deliveryTarget == OutboxDeliveryTarget.LOCAL_DOMAIN) {
                    "Domain events can only target LOCAL_DOMAIN"
                }
            OutboxMessageKind.INTEGRATION_EVENT,
            OutboxMessageKind.INTEGRATION_COMMAND ->
                require(deliveryTarget != OutboxDeliveryTarget.LOCAL_DOMAIN) {
                    "Integration messages cannot target LOCAL_DOMAIN"
                }
        }
    }
}

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
