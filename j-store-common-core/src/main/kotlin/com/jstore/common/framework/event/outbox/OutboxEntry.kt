package com.jstore.common.framework.event.outbox

import java.time.Instant

/**
 * Outbox 条目领域模型，表示一条待发布的领域事件记录。
 */
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
    val lastError: String? = null
)
