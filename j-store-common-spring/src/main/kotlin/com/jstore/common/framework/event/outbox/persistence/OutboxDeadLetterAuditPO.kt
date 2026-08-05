package com.jstore.common.framework.event.outbox.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "outbox_dead_letter_audit")
class OutboxDeadLetterAuditPO(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null,
    @Column(name = "outbox_entry_id", nullable = false, length = 36) var outboxEntryId: String = "",
    @Column(name = "event_id", length = 64) var eventId: String? = null,
    @Column(name = "operator_id", nullable = false, length = 128) var operatorId: String = "",
    @Column(name = "action", nullable = false, length = 32) var action: String = "",
    @Column(name = "reason", nullable = false, length = 1000) var reason: String = "",
    @Column(name = "result", nullable = false, length = 32) var result: String = "",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
)
