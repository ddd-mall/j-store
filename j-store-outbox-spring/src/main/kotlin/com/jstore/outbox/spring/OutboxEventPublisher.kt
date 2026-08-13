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
package com.jstore.outbox.spring

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventMetadata
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.outbox.*
import java.time.Instant
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 基于 Outbox 模式的事件发布者实现。
 *
 * 将领域事件序列化后写入 Outbox 表（状态为 PENDING）， 替代直接内存投递，作为生产环境 DomainEventPublisher 的默认实现。
 */
open class OutboxEventPublisher(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val eventSerializer: EventSerializer,
    private val snowFlakSequence: SnowFlakSequence,
    private val eventTypeRegistry: EventTypeRegistry = InMemoryEventTypeRegistry(),
    private val streamSequenceAllocator: OutboxStreamSequenceAllocator,
    private val relaySignal: OutboxRelaySignal = NoopOutboxRelaySignal,
) : DomainEventPublisher {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun publishEvent(event: DomainEvent) {
        publishEvents(listOf(event))
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun publishEvents(events: List<DomainEvent>) {
        if (events.isEmpty()) return

        val preparedEvents = events.map(::prepare)
        val streams = preparedEvents.map {
            OutboxStreamKey(OutboxTransportIds.LOCAL_DOMAIN, it.orderingKey)
        }
        val sequenceNumbers = streamSequenceAllocator.nextSequences(streams)
        check(sequenceNumbers.size == preparedEvents.size) {
            "Outbox stream allocator returned ${sequenceNumbers.size} sequences " +
                "for ${preparedEvents.size} domain events"
        }
        val now = Instant.now()
        val entries =
            preparedEvents.zip(sequenceNumbers) { prepared, sequenceNumber ->
                val metadata = prepared.metadata
                OutboxEntry(
                    id = snowFlakSequence.nextId().toString(),
                    eventId = metadata.eventId,
                    eventType = metadata.eventName,
                    eventClassName = prepared.eventClassName,
                    eventVersion = metadata.eventVersion,
                    payload = prepared.payload,
                    aggregateType = metadata.aggregateType,
                    aggregateId = metadata.aggregateId,
                    status = OutboxEntryStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                    occurredAt = metadata.occurredAt,
                    retryCount = 0,
                    orderingKey = prepared.orderingKey,
                    sequenceNo = sequenceNumber,
                )
            }
        outboxEntryRepository.saveAll(entries)
        relaySignal.signalAfterCommit()
    }

    private fun prepare(event: DomainEvent): PreparedDomainEvent {
        val metadata = event.metadata
        val eventType =
            event::class.java.getAnnotation(DomainEventType::class.java)
                ?: throw IllegalArgumentException(
                    "Outbox DomainEvent must be annotated with @DomainEventType: ${event::class.java.name}"
                )
        require(
            eventType.name == metadata.eventName && eventType.version == metadata.eventVersion
        ) {
            "DomainEvent metadata must match @DomainEventType: class=${event::class.java.name}, " +
                "metadata=${metadata.eventName}@${metadata.eventVersion}, " +
                "annotation=${eventType.name}@${eventType.version}"
        }
        val registeredEventClass =
            eventTypeRegistry.resolve(metadata.eventName, metadata.eventVersion)
        require(registeredEventClass == event::class.java) {
            "DomainEvent class must match startup registered @DomainEventType: " +
                "eventName=${metadata.eventName}, eventVersion=${metadata.eventVersion}, " +
                "registeredClass=${registeredEventClass.name}, publishingClass=${event::class.java.name}"
        }
        return PreparedDomainEvent(
            metadata = metadata,
            eventClassName = event::class.java.name,
            payload = eventSerializer.serialize(event),
            orderingKey = OutboxOrderingKeys.domain(metadata.aggregateType, metadata.aggregateId),
        )
    }

    private data class PreparedDomainEvent(
        val metadata: DomainEventMetadata,
        val eventClassName: String,
        val payload: String,
        val orderingKey: String,
    )
}
