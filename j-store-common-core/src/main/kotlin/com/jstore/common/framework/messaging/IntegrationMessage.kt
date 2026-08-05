package com.jstore.common.framework.messaging

import com.jstore.common.framework.event.outbox.OutboxDeliveryTarget
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID

/** A stable message contract crossing a bounded-context or process boundary. */
interface IntegrationMessage {
    val messageId: String
    val messageName: String
    val messageVersion: Int
    val occurredAt: Instant
    val partitionKey: String
    val correlationId: String
    val causationId: String?
        get() = null

    val tenantId: String?
        get() = null

    val destination: String

    val metadata: IntegrationMessageMetadata
        get() =
            IntegrationMessageMetadata(
                messageId = messageId,
                messageName = messageName,
                messageVersion = messageVersion,
                occurredAt = occurredAt,
                partitionKey = partitionKey,
                correlationId = correlationId,
                causationId = causationId,
                tenantId = tenantId,
            )
}

/** A fact that may be observed by zero or more consumers. */
interface IntegrationEvent : IntegrationMessage

/** An intention addressed to one logical owning context. */
interface IntegrationCommand : IntegrationMessage

data class IntegrationMessageMetadata(
    val messageId: String,
    val messageName: String,
    val messageVersion: Int,
    val occurredAt: Instant,
    val partitionKey: String,
    val correlationId: String,
    val causationId: String? = null,
    val tenantId: String? = null,
) {
    init {
        require(messageId.isNotBlank()) { "messageId must not be blank" }
        require(messageName.isNotBlank()) { "messageName must not be blank" }
        require(messageVersion > 0) { "messageVersion must be greater than zero" }
        require(partitionKey.isNotBlank()) { "partitionKey must not be blank" }
        require(correlationId.isNotBlank()) { "correlationId must not be blank" }
        require(causationId == null || causationId.isNotBlank()) {
            "causationId must be null or non-blank"
        }
        require(tenantId == null || tenantId.isNotBlank()) { "tenantId must be null or non-blank" }
    }
}

enum class IntegrationMessagingMode {
    LOCAL,
    BROKER,
    HYBRID,
}

class IntegrationPublicationPlanner(private val mode: IntegrationMessagingMode) {
    fun targets(): List<OutboxDeliveryTarget> =
        when (mode) {
            IntegrationMessagingMode.LOCAL -> listOf(OutboxDeliveryTarget.LOCAL_INTEGRATION)
            IntegrationMessagingMode.BROKER -> listOf(OutboxDeliveryTarget.BROKER)
            IntegrationMessagingMode.HYBRID ->
                listOf(
                    OutboxDeliveryTarget.LOCAL_INTEGRATION,
                    OutboxDeliveryTarget.BROKER,
                )
        }
}

interface IntegrationMessagePublisher {
    fun publish(message: IntegrationMessage)
}

fun stableIntegrationMessageId(
    messageName: String,
    messageVersion: Int,
    partitionKey: String,
    occurredAt: Instant,
): String =
    UUID.nameUUIDFromBytes(
            "$messageName|$messageVersion|$partitionKey|$occurredAt"
                .toByteArray(StandardCharsets.UTF_8)
        )
        .toString()
