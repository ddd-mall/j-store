package com.jstore.common.framework.event

import java.time.Instant
import java.util.UUID

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainEventType(
    val name: String,
    val version: Int = 1,
)

/** Immutable domain fact emitted by an aggregate or domain service. */
interface DomainEvent {
    val eventId: String
    val eventName: String
    val eventVersion: Int
    val occurredAt: Instant
    val aggregateType: String
    val aggregateId: String

    /** Stable envelope metadata used by outbox delivery, diagnostics, and idempotent consumers. */
    val metadata: DomainEventMetadata
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

/** Creates the stable ID once when a new event instance is constructed. */
fun newDomainEventId(): String = UUID.randomUUID().toString()

data class DomainEventMetadata(
    val eventId: String,
    val eventName: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val aggregateType: String,
    val aggregateId: String,
)
