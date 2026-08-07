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
package com.jstore.common.framework.event.outbox

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.order.domain.order.event.OrderCompletedEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant

/**
 * JacksonEventSerializer 单元测试
 *
 * Validates: Requirements 4.4, 4.5
 */
class JacksonEventSerializerTest :
    FunSpec({
        val objectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        val serializer = JacksonEventSerializer(objectMapper)

        test("deserialize resolves event by stable event name and version") {
            val registry = InMemoryEventTypeRegistry()
            registry.register("order.completed", 1, OrderCompletedEvent::class.java)
            val registryBackedSerializer = JacksonEventSerializer(objectMapper, registry)
            val payload = """{"orderId":{"value":42},"occurredAt":"2025-01-01T00:00:00Z"}"""

            val restored =
                registryBackedSerializer.deserialize(
                    payload,
                    eventName = "order.completed",
                    eventVersion = 1,
                )

            restored::class.java shouldBe OrderCompletedEvent::class.java
        }

        test("deserialize upcasts old payload version before resolving event class") {
            val registry = InMemoryEventTypeRegistry()
            registry.register("test.versioned-event", 2, VersionedTestEvent::class.java)
            val upcasterRegistry =
                InMemoryEventUpcasterRegistry(
                    listOf(
                        object : EventUpcaster {
                            override val eventName: String = "test.versioned-event"
                            override val sourceVersion: Int = 1
                            override val targetVersion: Int = 2

                            override fun upcast(payload: String): String {
                                val node = objectMapper.readTree(payload)
                                return objectMapper.writeValueAsString(
                                    mapOf("newValue" to node.get("oldValue").asText())
                                )
                            }
                        }
                    )
                )
            val registryBackedSerializer =
                JacksonEventSerializer(objectMapper, registry, upcasterRegistry)

            val restored =
                registryBackedSerializer.deserialize(
                    payload = """{"oldValue":"legacy"}""",
                    eventName = "test.versioned-event",
                    eventVersion = 1,
                )

            restored shouldBe VersionedTestEvent(newValue = "legacy")
        }

        test(
            "deserialize with unknown event type throws OutboxSerializationException containing type info"
        ) {
            val unknownType = "com.jstore.nonexistent.FakeEvent"
            val payload = """{"source":"test"}"""

            val ex =
                shouldThrow<OutboxSerializationException> {
                    serializer.deserialize(payload, unknownType, 1)
                }
            ex.message shouldContain unknownType
        }

        test(
            "deserialize with malformed JSON throws OutboxSerializationException containing payload summary"
        ) {
            val eventType = "order.completed"
            val registry = InMemoryEventTypeRegistry()
            registry.register(eventType, 1, OrderCompletedEvent::class.java)
            val registryBackedSerializer = JacksonEventSerializer(objectMapper, registry)
            val malformedJson = "{this is not valid json!!!"

            val ex =
                shouldThrow<OutboxSerializationException> {
                    registryBackedSerializer.deserialize(malformedJson, eventType, 1)
                }
            ex.message shouldContain malformedJson
        }

        test("deserialize with long malformed JSON truncates payload summary to 200 chars") {
            val eventType = "order.completed"
            val registry = InMemoryEventTypeRegistry()
            registry.register(eventType, 1, OrderCompletedEvent::class.java)
            val registryBackedSerializer = JacksonEventSerializer(objectMapper, registry)
            val longPayload = "x".repeat(300)

            val ex =
                shouldThrow<OutboxSerializationException> {
                    registryBackedSerializer.deserialize(longPayload, eventType, 1)
                }
            ex.message shouldContain longPayload.substring(0, 200) + "..."
        }
    })

@DomainEventType(name = "test.versioned-event", version = 2)
data class VersionedTestEvent(
    val newValue: String,
    override val source: Any = "source",
    override val occurredAt: Instant = Instant.parse("2025-01-01T00:00:00Z"),
) : ExplicitDomainEvent {
    override val eventName: String = "test.versioned-event"
    override val eventVersion: Int = 2
    override val aggregateType: String = "Test"
    override val aggregateId: String = newValue
    override val eventId: String =
        stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
