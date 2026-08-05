package com.jstore.common.framework.event

import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.framework.Identifier
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PendingDomainEventsTest {
    @Test
    fun `new event IDs remain unique for otherwise identical events`() {
        val occurredAt = Instant.parse("2026-01-01T00:00:00Z")

        val first = TestEvent(eventId = newDomainEventId(), occurredAt = occurredAt)
        val second = TestEvent(eventId = newDomainEventId(), occurredAt = occurredAt)

        assertNotEquals(first.eventId, second.eventId)
    }

    @Test
    fun `publisher failure preserves pending events for transaction retry`() {
        val first = TestEvent()
        val second = TestEvent()
        val aggregate =
            TestAggregate().apply {
                record(first)
                record(second)
            }
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: DomainEvent) {
                    if (event === first) throw IllegalStateException("outbox unavailable")
                }
            }

        assertFailsWith<IllegalStateException> { aggregate.publishPendingEvents(publisher) }

        assertEquals(listOf(first, second), aggregate.pendingDomainEvents())
    }

    @Test
    fun `successful publication acknowledges the stable event snapshot`() {
        val events = listOf(TestEvent(), TestEvent())
        val aggregate = TestAggregate().apply { events.forEach(::record) }
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: DomainEvent) = Unit
            }

        aggregate.publishPendingEvents(publisher)

        assertEquals(emptyList(), aggregate.pendingDomainEvents())
    }

    private data object TestId : Identifier

    private class TestEvent(
        override val eventId: String = newDomainEventId(),
        override val occurredAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    ) : DomainEvent {
        override val eventName = "test.event"
        override val eventVersion = 1
        override val aggregateType = "test"
        override val aggregateId = "1"
    }

    private class TestAggregate : EventRecordingAggregateRoot<TestId>() {
        override val id = TestId

        fun record(event: DomainEvent) = raise(event)
    }
}
