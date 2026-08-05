package com.jstore.common.framework.event

import com.jstore.common.framework.AgreeGate
import com.jstore.common.framework.Identify
import java.util.LinkedList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PendingDomainEventsTest {
    @Test
    fun `publisher failure preserves pending events for transaction retry`() {
        val first = TestEvent()
        val second = TestEvent()
        val aggregate = TestAggregate(LinkedList(listOf(first, second)))
        val publisher =
            object : DomainEventPublisher {
                override fun <T : DomainEvent> publishEvent(event: T) {
                    if (event === first) throw IllegalStateException("outbox unavailable")
                }
            }

        assertFailsWith<IllegalStateException> { aggregate.publishPendingEvents(publisher) }

        assertEquals(listOf(first, second), aggregate.domainEventQueue.toList())
    }

    @Test
    fun `successful publication acknowledges the stable event snapshot`() {
        val events = listOf(TestEvent(), TestEvent())
        val aggregate = TestAggregate(LinkedList(events))
        val publisher =
            object : DomainEventPublisher {
                override fun <T : DomainEvent> publishEvent(event: T) = Unit
            }

        aggregate.publishPendingEvents(publisher)

        assertEquals(emptyList(), aggregate.domainEventQueue.toList())
    }

    private data object TestId : Identify

    private class TestEvent : DomainEvent {
        override val source: Any = this
    }

    private class TestAggregate(override val domainEventQueue: LinkedList<DomainEvent>) :
        AgreeGate<TestId> {
        override val id = TestId
    }
}
