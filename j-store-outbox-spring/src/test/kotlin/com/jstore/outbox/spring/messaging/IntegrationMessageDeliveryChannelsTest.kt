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

import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessageEnvelope
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.messaging.IntegrationMessageTransport
import com.jstore.messaging.LocalIntegrationMessageBus
import com.jstore.messaging.MessageDeliveryOrder
import com.jstore.outbox.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class IntegrationMessageDeliveryChannelsTest {
    @Test
    fun `broker envelope carries the persisted ordering stream position`() {
        var delivered: IntegrationMessageEnvelope? = null
        val transport =
            object : IntegrationMessageTransport {
                override val transportId = "kafka"

                override fun publish(envelope: IntegrationMessageEnvelope) {
                    delivered = envelope
                }
            }
        val channel = TransportIntegrationMessageDeliveryChannel(transport)
        val now = Instant.parse("2026-08-10T00:00:00Z")
        val acceptBefore = now.plusSeconds(15)

        channel.deliver(
            OutboxEntry(
                id = "entry-1",
                eventType = "order.confirmed",
                payload = "{}",
                aggregateType = "orders.events",
                aggregateId = "42",
                status = OutboxEntryStatus.PENDING,
                createdAt = now,
                updatedAt = now,
                messageKind = OutboxMessageKind.INTEGRATION_COMMAND,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
                transportId = "kafka",
                destination = "orders.events",
                logicalDestination = "order.events",
                deliveryProfile = "CHECKOUT_CRITICAL",
                acceptBefore = acceptBefore,
                partitionKey = "42",
                merchantScopeId = "merchant-7",
                deploymentScopeId = "site-jp",
                orderingKey = "orders.events:42",
                sequenceNo = 19,
            )
        )

        assertEquals("orders.events:42", delivered?.orderingKey)
        assertEquals(19, delivered?.sequenceNo)
        assertEquals("orders.events", delivered?.destination)
        assertEquals("order.events", delivered?.logicalDestination)
        assertEquals("CHECKOUT_CRITICAL", delivered?.deliveryProfile)
        assertEquals(acceptBefore, delivered?.acceptBefore)
        assertEquals("merchant-7", delivered?.merchantScopeId)
        assertEquals("site-jp", delivered?.deploymentScopeId)
    }

    @Test
    fun `local delivery passes the persisted ordering position to the consumer bus`() {
        var order: MessageDeliveryOrder? = null
        val bus =
            object : LocalIntegrationMessageBus {
                override fun publish(message: IntegrationMessage) = Unit

                override fun publish(
                    message: IntegrationMessage,
                    deliveryOrder: MessageDeliveryOrder,
                ) {
                    order = deliveryOrder
                }

                override fun register(handler: IntegrationMessageHandler<*>) = Unit

                override fun unregister(handler: IntegrationMessageHandler<*>) = Unit
            }
        val message = com.jstore.outbox.spring.messaging.message
        val serializer =
            object : IntegrationMessageSerializer {
                override fun serialize(message: IntegrationMessage) = "{}"

                override fun deserialize(
                    payload: String,
                    messageName: String,
                    messageVersion: Int,
                ) = message
            }
        val now = Instant.parse("2026-08-10T00:00:00Z")

        LocalIntegrationMessageDeliveryChannel(serializer, bus)
            .deliver(
                OutboxEntry(
                    id = "entry-local",
                    eventType = message.messageName,
                    payload = "{}",
                    aggregateType = message.destination,
                    aggregateId = message.partitionKey,
                    status = OutboxEntryStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                    messageKind = OutboxMessageKind.INTEGRATION_COMMAND,
                    deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
                    transportId = "local",
                    orderingKey = "inventory.commands:42",
                    sequenceNo = 8,
                )
            )

        assertEquals(MessageDeliveryOrder("local", "inventory.commands:42", 8), order)
    }
}
