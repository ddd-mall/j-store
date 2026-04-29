package com.jstore.common.framework.event.outbox.persistence

import com.jstore.common.framework.event.outbox.OutboxEntryStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "outbox_entry")
class OutboxEntryPO(
    @Id
    @Column(name = "id", length = 36)
    var id: String = "",

    @Column(name = "event_type", nullable = false, length = 512)
    var eventType: String = "",

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    var payload: String = "",

    @Column(name = "aggregate_type", nullable = false, length = 256)
    var aggregateType: String = "",

    @Column(name = "aggregate_id", nullable = false, length = 128)
    var aggregateId: String = "",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OutboxEntryStatus = OutboxEntryStatus.PENDING,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "next_attempt_at", nullable = false)
    var nextAttemptAt: Instant = Instant.now(),

    @Column(name = "locked_by", length = 128)
    var lockedBy: String? = null,

    @Column(name = "locked_at")
    var lockedAt: Instant? = null,

    @Column(name = "locked_until")
    var lockedUntil: Instant? = null,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null
)
