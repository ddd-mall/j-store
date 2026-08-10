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
package com.jstore.outbox.spring.messaging

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.messaging.*
import com.jstore.outbox.*
import com.jstore.outbox.IntegrationMessageSerializer
import com.jstore.outbox.IntegrationMessageTypeRegistry
import com.jstore.outbox.OutboxDeliveryTarget
import com.jstore.outbox.OutboxEntry
import com.jstore.outbox.OutboxEntryRepository
import com.jstore.outbox.OutboxEntryStatus
import com.jstore.outbox.OutboxMessageKind
import java.time.Instant
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

open class OutboxIntegrationMessagePublisher(
    private val repository: OutboxEntryRepository,
    private val serializer: IntegrationMessageSerializer,
    private val sequence: SnowFlakSequence,
    private val typeRegistry: IntegrationMessageTypeRegistry,
    private val publicationPlanner: IntegrationTransportPlanner,
) : IntegrationMessagePublisher {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun publish(message: IntegrationMessage) {
        val annotation =
            message::class.java.getAnnotation(IntegrationMessageType::class.java)
                ?: throw IllegalArgumentException(
                    "IntegrationMessage must be annotated with @IntegrationMessageType: " +
                        message::class.java.name
                )
        require(
            annotation.name == message.messageName && annotation.version == message.messageVersion
        ) {
            "IntegrationMessage metadata must match @IntegrationMessageType: " +
                "class=${message::class.java.name}"
        }
        require(
            typeRegistry.resolve(message.messageName, message.messageVersion) == message::class.java
        ) {
            "IntegrationMessage class must match the registered message type: " +
                "${message.messageName}@${message.messageVersion}"
        }

        val now = Instant.now()
        val payload = serializer.serialize(message)
        val kind =
            when (message) {
                is IntegrationCommand -> OutboxMessageKind.INTEGRATION_COMMAND
                is IntegrationEvent -> OutboxMessageKind.INTEGRATION_EVENT
                else -> error("Unsupported IntegrationMessage marker: ${message::class.java.name}")
            }

        publicationPlanner.targets().forEach { transportId ->
            repository.save(
                OutboxEntry(
                    id = sequence.nextId().toString(),
                    eventId = message.messageId,
                    eventType = message.messageName,
                    eventClassName = message::class.java.name,
                    eventVersion = message.messageVersion,
                    payload = payload,
                    aggregateType = message.destination,
                    aggregateId = message.partitionKey,
                    status = OutboxEntryStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                    occurredAt = message.occurredAt,
                    messageKind = kind,
                    deliveryTarget =
                        if (transportId == OutboxTransportIds.LOCAL) {
                            OutboxDeliveryTarget.LOCAL_INTEGRATION
                        } else {
                            OutboxDeliveryTarget.BROKER
                        },
                    transportId = transportId,
                    destination = message.destination,
                    partitionKey = message.partitionKey,
                    correlationId = message.correlationId,
                    causationId = message.causationId,
                    tenantId = message.tenantId,
                )
            )
        }
    }
}
