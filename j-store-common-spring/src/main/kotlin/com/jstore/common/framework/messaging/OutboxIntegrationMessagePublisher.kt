package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.outbox.IntegrationMessageSerializer
import com.jstore.common.framework.event.outbox.IntegrationMessageType
import com.jstore.common.framework.event.outbox.IntegrationMessageTypeRegistry
import com.jstore.common.framework.event.outbox.OutboxDeliveryTarget
import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxEntryRepository
import com.jstore.common.framework.event.outbox.OutboxEntryStatus
import com.jstore.common.framework.event.outbox.OutboxMessageKind
import com.jstore.common.persistent.SnowFlakSequence
import java.time.Instant
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

open class OutboxIntegrationMessagePublisher(
    private val repository: OutboxEntryRepository,
    private val serializer: IntegrationMessageSerializer,
    private val sequence: SnowFlakSequence,
    private val typeRegistry: IntegrationMessageTypeRegistry,
    private val publicationPlanner: IntegrationPublicationPlanner,
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

        publicationPlanner.targets().forEach { target ->
            check(target != OutboxDeliveryTarget.LOCAL_DOMAIN) {
                "Integration messages cannot target LOCAL_DOMAIN"
            }
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
                    deliveryTarget = target,
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
