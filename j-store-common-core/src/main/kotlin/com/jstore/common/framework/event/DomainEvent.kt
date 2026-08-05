package com.jstore.common.framework.event

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/** Marker for domain facts emitted by aggregates or domain services. */
interface DomainEvent {
    val source: Any

    /** Stable envelope metadata used by outbox delivery, diagnostics, and idempotent consumers. */
    val metadata: DomainEventMetadata
        get() = DomainEventMetadata.from(this)
}

interface ExplicitDomainEvent : DomainEvent {
    val eventId: String
    val eventName: String
    val eventVersion: Int
    val occurredAt: Instant
    val aggregateType: String
    val aggregateId: String

    override val metadata: DomainEventMetadata
        get() =
            DomainEventMetadata(
                eventId = eventId,
                eventName = eventName,
                eventVersion = eventVersion,
                occurredAt = occurredAt,
                aggregateType = aggregateType,
                aggregateId = aggregateId,
            )
}

fun stableDomainEventId(
    eventName: String,
    eventVersion: Int,
    aggregateType: String,
    aggregateId: String,
    occurredAt: Instant,
): String {
    return UUID.nameUUIDFromBytes(
            "$eventName|$eventVersion|$aggregateType|$aggregateId|$occurredAt"
                .toByteArray(StandardCharsets.UTF_8)
        )
        .toString()
}

data class DomainEventMetadata(
    val eventId: String,
    val eventName: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val aggregateType: String,
    val aggregateId: String,
) {
    companion object {
        fun from(event: DomainEvent): DomainEventMetadata {
            if (event is ExplicitDomainEvent) {
                return event.metadata
            }
            throw IllegalArgumentException(
                "DomainEvent must implement ExplicitDomainEvent to provide stable envelope metadata: ${event::class.java.name}"
            )
        }
    }
}

// DONE: 发件箱模式（Transactional Outbox）基础设施已实现。
// PARTIAL: 死信队列以 outbox DEAD_LETTER 状态实现，已具备基础查询和 requeue，仍需业务运维界面/告警策略。
// TODO: 事件溯源（Event Sourcing）尚未实现。
