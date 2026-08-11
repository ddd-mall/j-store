/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    @Test
    fun `pending events are submitted as one ordered batch`() {
        val events = listOf(TestEvent(), TestEvent())
        val aggregate = TestAggregate().apply { events.forEach(::record) }
        val publishedBatches = mutableListOf<List<DomainEvent>>()
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: DomainEvent) =
                    error("single-event publication must not be used")

                override fun publishEvents(events: List<DomainEvent>) {
                    publishedBatches += events.toList()
                }
            }

        aggregate.publishPendingEvents(publisher)

        assertEquals(listOf<List<DomainEvent>>(events), publishedBatches)
        assertEquals(emptyList(), aggregate.pendingDomainEvents())
    }

    @Test
    fun `batch failure preserves every event in the stable snapshot`() {
        val events = listOf(TestEvent(), TestEvent())
        val aggregate = TestAggregate().apply { events.forEach(::record) }
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: DomainEvent) = Unit

                override fun publishEvents(events: List<DomainEvent>) {
                    throw IllegalStateException("outbox batch unavailable")
                }
            }

        assertFailsWith<IllegalStateException> { aggregate.publishPendingEvents(publisher) }

        assertEquals(events, aggregate.pendingDomainEvents())
    }

    @Test
    fun `events recorded during batch publication remain pending`() {
        val initial = TestEvent()
        val recordedDuringPublication = TestEvent()
        val aggregate = TestAggregate().apply { record(initial) }
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: DomainEvent) = Unit

                override fun publishEvents(events: List<DomainEvent>) {
                    assertEquals(listOf(initial), events)
                    aggregate.record(recordedDuringPublication)
                }
            }

        aggregate.publishPendingEvents(publisher)

        assertEquals(listOf(recordedDuringPublication), aggregate.pendingDomainEvents())
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
