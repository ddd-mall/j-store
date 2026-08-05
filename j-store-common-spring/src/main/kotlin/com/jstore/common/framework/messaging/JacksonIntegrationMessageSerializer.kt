package com.jstore.common.framework.messaging

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.common.framework.event.outbox.IntegrationMessageSerializer
import com.jstore.common.framework.event.outbox.IntegrationMessageTypeRegistry
import com.jstore.common.framework.event.outbox.OutboxSerializationException

class JacksonIntegrationMessageSerializer(
    private val objectMapper: ObjectMapper,
    private val typeRegistry: IntegrationMessageTypeRegistry,
) : IntegrationMessageSerializer {
    override fun serialize(message: IntegrationMessage): String =
        objectMapper.writeValueAsString(message)

    override fun deserialize(
        payload: String,
        messageName: String,
        messageVersion: Int,
    ): IntegrationMessage =
        try {
            objectMapper
                .readerFor(typeRegistry.resolve(messageName, messageVersion))
                .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(payload)
        } catch (exception: Exception) {
            throw OutboxSerializationException(
                "Failed to deserialize integration message: messageName=$messageName, " +
                    "messageVersion=$messageVersion",
                exception,
            )
        }
}
