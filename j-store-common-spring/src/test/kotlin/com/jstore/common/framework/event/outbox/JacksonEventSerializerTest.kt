package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain

/**
 * JacksonEventSerializer 单元测试
 *
 * Validates: Requirements 4.4, 4.5
 */
class JacksonEventSerializerTest : FunSpec({

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    val serializer = JacksonEventSerializer(objectMapper)

    test("deserialize with unknown event type throws OutboxSerializationException containing type info") {
        val unknownType = "com.jstore.nonexistent.FakeEvent"
        val payload = """{"source":"test"}"""

        val ex = shouldThrow<OutboxSerializationException> {
            serializer.deserialize(payload, unknownType)
        }
        ex.message shouldContain unknownType
    }

    test("deserialize with malformed JSON throws OutboxSerializationException containing payload summary") {
        val eventType = "com.jstore.order.domain.order.event.OrderShippedEvent"
        val malformedJson = "{this is not valid json!!!"

        val ex = shouldThrow<OutboxSerializationException> {
            serializer.deserialize(malformedJson, eventType)
        }
        ex.message shouldContain malformedJson
    }

    test("deserialize with long malformed JSON truncates payload summary to 200 chars") {
        val eventType = "com.jstore.order.domain.order.event.OrderShippedEvent"
        val longPayload = "x".repeat(300)

        val ex = shouldThrow<OutboxSerializationException> {
            serializer.deserialize(longPayload, eventType)
        }
        ex.message shouldContain longPayload.substring(0, 200) + "..."
    }
})
