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
package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.outbox.IntegrationMessageSerializer
import com.jstore.common.framework.event.outbox.OutboxDeliveryChannel
import com.jstore.common.framework.event.outbox.OutboxDeliveryTarget
import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxMessageKind

class LocalIntegrationMessageDeliveryChannel(
    private val serializer: IntegrationMessageSerializer,
    private val bus: LocalIntegrationMessageBus,
) : OutboxDeliveryChannel {
    override val target: OutboxDeliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION

    override fun deliver(entry: OutboxEntry) {
        requireIntegration(entry)
        bus.publish(serializer.deserialize(entry.payload, entry.eventType, entry.eventVersion))
    }
}

class BrokerIntegrationMessageDeliveryChannel(
    private val transport: BrokerIntegrationMessageTransport
) : OutboxDeliveryChannel {
    override val target: OutboxDeliveryTarget = OutboxDeliveryTarget.BROKER

    override fun deliver(entry: OutboxEntry) {
        requireIntegration(entry)
        transport.publish(
            IntegrationMessageEnvelope(
                messageId = entry.eventId,
                messageName = entry.eventType,
                messageVersion = entry.eventVersion,
                messageKind = entry.messageKind,
                destination = entry.destination,
                partitionKey = entry.partitionKey,
                correlationId = entry.correlationId,
                causationId = entry.causationId,
                tenantId = entry.tenantId,
                occurredAt = entry.occurredAt,
                payload = entry.payload,
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
