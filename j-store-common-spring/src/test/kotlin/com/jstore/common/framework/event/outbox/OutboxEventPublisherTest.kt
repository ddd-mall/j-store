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
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant
import org.mockito.kotlin.*

/**
 * OutboxEventPublisher 单元测试
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class OutboxEventPublisherTest :
    FunSpec({
        val objectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        test("publishEvent creates OutboxEntry with correct fields and calls save") {
            val mockRepository =
                mock<OutboxEntryRepository> {
                    on { save(any()) } doAnswer { it.arguments[0] as OutboxEntry }
                }
            val eventTypeRegistry =
                InMemoryEventTypeRegistry().apply {
                    register("order.created", 2, OrderCreatedEvent::class.java)
                }
            val serializer = JacksonEventSerializer(objectMapper)
            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    eventTypeRegistry,
                )

            val event =
                OrderCreatedEvent(
                    orderId = OrderId(42L),
                    merchantId = MerchantId(7),
                    payableAmount = Price.ofFen(9999),
                    currency = "CNY",
                    items = listOf(OrderItemSnapshot(1L, 2)),
                    occurredAt = Instant.parse("2025-01-01T00:00:00Z"),
                )

            publisher.publishEvent(event)

            val captor = argumentCaptor<OutboxEntry>()
            verify(mockRepository, times(1)).save(captor.capture())

            val saved = captor.firstValue
            saved.id shouldNotBe ""
            saved.eventId shouldNotBe ""
            saved.eventType shouldBe "order.created"
            saved.eventClassName shouldBe "com.jstore.order.domain.order.event.OrderCreatedEvent"
            saved.eventVersion shouldBe 2
            saved.occurredAt shouldBe event.occurredAt
            saved.status shouldBe OutboxEntryStatus.PENDING
            saved.retryCount shouldBe 0
            saved.payload shouldNotBe ""
        }

        test("serialization failure propagates exception ensuring business transaction rollback") {
            val mockRepository = mock<OutboxEntryRepository>()
            val mockSerializer =
                mock<EventSerializer> {
                    on { serialize(any()) } doThrow RuntimeException("serialization error")
                }
            val eventTypeRegistry =
                InMemoryEventTypeRegistry().apply {
                    register("order.created", 2, OrderCreatedEvent::class.java)
                }

            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    mockSerializer,
                    SnowFlakSequence(1, 1),
                    eventTypeRegistry,
                )

            val event =
                OrderCreatedEvent(
                    orderId = OrderId(1L),
                    merchantId = MerchantId(7),
                    payableAmount = Price.ofFen(100),
                    currency = "CNY",
                    items = listOf(OrderItemSnapshot(1L, 1)),
                    occurredAt = Instant.now(),
                )

            shouldThrow<RuntimeException> {
                publisher.publishEvent(event)
            }

            verify(mockRepository, never()).save(any())
        }

        test("publishEvent fails when event type was not registered during startup") {
            val mockRepository = mock<OutboxEntryRepository>()
            val serializer = JacksonEventSerializer(objectMapper, InMemoryEventTypeRegistry())
            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    InMemoryEventTypeRegistry(),
                )

            val event =
                OrderCreatedEvent(
                    orderId = OrderId(1L),
                    merchantId = MerchantId(7),
                    payableAmount = Price.ofFen(100),
                    currency = "CNY",
                    items = listOf(OrderItemSnapshot(1L, 1)),
                    occurredAt = Instant.now(),
                )

            shouldThrow<OutboxSerializationException> {
                publisher.publishEvent(event)
            }

            verify(mockRepository, never()).save(any())
        }
    })
