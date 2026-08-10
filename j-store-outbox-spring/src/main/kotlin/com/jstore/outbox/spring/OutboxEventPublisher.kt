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
) : DomainEventPublisher {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun publishEvent(event: DomainEvent) {
        val now = Instant.now()
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
        val entry =
            OutboxEntry(
                id = snowFlakSequence.nextId().toString(),
                eventId = metadata.eventId,
                eventType = metadata.eventName,
                eventClassName = event::class.java.name,
                eventVersion = metadata.eventVersion,
                payload = eventSerializer.serialize(event),
                aggregateType = metadata.aggregateType,
                aggregateId = metadata.aggregateId,
                status = OutboxEntryStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                occurredAt = metadata.occurredAt,
                retryCount = 0,
            )
        outboxEntryRepository.save(entry)
    }
}
