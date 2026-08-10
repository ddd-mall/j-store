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

import com.jstore.messaging.*
import com.jstore.outbox.*
import com.jstore.outbox.IntegrationMessageSerializer
import com.jstore.outbox.OutboxDeliveryChannel
import com.jstore.outbox.OutboxEntry
import com.jstore.outbox.OutboxMessageKind

class LocalIntegrationMessageDeliveryChannel(
    private val serializer: IntegrationMessageSerializer,
    private val bus: LocalIntegrationMessageBus,
) : OutboxDeliveryChannel {
    override val transportId: String = OutboxTransportIds.LOCAL

    override fun deliver(entry: OutboxEntry) {
        check(entry.transportId == transportId) {
            "LOCAL integration channel cannot deliver transport ${entry.transportId}"
        }
        requireIntegration(entry)
        bus.publish(
            serializer.deserialize(entry.payload, entry.eventType, entry.eventVersion),
            MessageDeliveryOrder(entry.transportId, entry.orderingKey, entry.sequenceNo),
        )
    }
}

class TransportIntegrationMessageDeliveryChannel(
    private val transport: IntegrationMessageTransport
) : OutboxDeliveryChannel {
    override val transportId: String = transport.transportId

    override fun deliver(entry: OutboxEntry) {
        check(entry.transportId == transportId) {
            "Transport channel $transportId cannot deliver transport ${entry.transportId}"
        }
        requireIntegration(entry)
        transport.publish(
            IntegrationMessageEnvelope(
                transportId = entry.transportId,
                messageId = entry.eventId,
                messageName = entry.eventType,
                messageVersion = entry.eventVersion,
                messageKind =
                    when (entry.messageKind) {
                        OutboxMessageKind.INTEGRATION_EVENT -> IntegrationMessageKind.EVENT
                        OutboxMessageKind.INTEGRATION_COMMAND -> IntegrationMessageKind.COMMAND
                        OutboxMessageKind.DOMAIN_EVENT ->
                            error("Domain event cannot use broker transport")
                    },
                destination = entry.destination,
                partitionKey = entry.partitionKey,
                correlationId = entry.correlationId,
                causationId = entry.causationId,
                tenantId = entry.tenantId,
                occurredAt = entry.occurredAt,
                payload = entry.payload,
                orderingKey = entry.orderingKey,
                sequenceNo = entry.sequenceNo,
            )
        )
    }
}

private fun requireIntegration(entry: OutboxEntry) {
    check(
        entry.messageKind == OutboxMessageKind.INTEGRATION_EVENT ||
            entry.messageKind == OutboxMessageKind.INTEGRATION_COMMAND
    ) {
        "Integration delivery channel cannot deliver ${entry.messageKind}"
    }
}
