package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.outbox.OutboxMessageKind
import java.time.Instant

data class IntegrationMessageEnvelope(
    val messageId: String,
    val messageName: String,
    val messageVersion: Int,
    val messageKind: OutboxMessageKind,
    val destination: String,
    val partitionKey: String,
    val correlationId: String,
    val causationId: String?,
    val tenantId: String?,
    val occurredAt: Instant,
    val payload: String,
)

/** SPI implemented by a concrete Kafka, AMQP, or cloud messaging adapter. */
interface BrokerIntegrationMessageTransport {
    /** Returns only after the broker has acknowledged accepting the message. */
    fun publish(envelope: IntegrationMessageEnvelope)
}
