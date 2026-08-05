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
