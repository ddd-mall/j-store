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
package com.jstore.common.framework

import com.jstore.common.framework.event.DomainEvent

/** Marks an entity as an aggregate consistency boundary. */
interface AggregateRoot<I : Identifier> : Entity<I>

/** Event recording capability kept separate from the aggregate-root marker. */
interface RecordsDomainEvents {
    fun pendingDomainEvents(): List<DomainEvent>

    fun acknowledgeDomainEvents(eventIds: Set<String>)
}

/**
 * Base class for aggregates that record domain events.
 *
 * The mutable collection is private. Callers receive a stable snapshot and can only acknowledge
 * events by stable ID after publication succeeds.
 */
abstract class EventRecordingAggregateRoot<I : Identifier> : AggregateRoot<I>, RecordsDomainEvents {
    private val domainEvents = mutableListOf<DomainEvent>()

    protected fun raise(event: DomainEvent) {
        require(domainEvents.none { it.eventId == event.eventId }) {
            "Duplicate pending domain event id: ${event.eventId}"
        }
        domainEvents += event
    }

    override fun pendingDomainEvents(): List<DomainEvent> = domainEvents.toList()

    override fun acknowledgeDomainEvents(eventIds: Set<String>) {
        require(eventIds.size == domainEvents.count { it.eventId in eventIds }) {
            "Cannot acknowledge unknown or duplicate pending domain event IDs"
        }
        domainEvents.removeAll { it.eventId in eventIds }
    }
}
