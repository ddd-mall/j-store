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
package com.jstore.outbox.spring

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
import com.jstore.outbox.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant
import org.mockito.kotlin.*
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

private fun orderCreatedEvent(orderId: OrderId) =
    OrderCreatedEvent(
        orderId = orderId,
        merchantId = MerchantId(7),
        payableAmount = Price.ofFen(9999),
        currency = "CNY",
        items = listOf(orderItemSnapshot(quantity = 2)),
        occurredAt = Instant.parse("2025-01-01T00:00:00Z"),
    )

private fun orderItemSnapshot(quantity: Int = 1) =
    OrderItemSnapshot(
        spuId = 1,
        skuId = 1,
        quantity = quantity,
        catalogSnapshotVersion = 1,
        unitPrice = Price.ofFen(9999),
        offerId = 1,
        storeId = 1,
        offerVersion = 1,
        fulfillmentNodeId = "DEFAULT",
        channelId = "ONLINE",
    )

/**
 * OutboxEventPublisher 单元测试
 *
 * Validates: Requirements 1.1, 1.2, 1.3
 */
class OutboxEventPublisherTest :
    FunSpec({
        afterTest {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization()
            }
            TransactionSynchronizationManager.clear()
        }
        val objectMapper =
            ObjectMapper()
                .registerKotlinModule()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

        test("publishEvent creates OutboxEntry with correct fields and calls save") {
            val mockRepository =
                mock<OutboxEntryRepository> {
                    on { saveAll(any()) } doAnswer
                        @Suppress("UNCHECKED_CAST") { it.arguments[0] as List<OutboxEntry> }
                }
            val eventTypeRegistry =
                InMemoryEventTypeRegistry().apply {
                    register("order.created", 4, OrderCreatedEvent::class.java)
                }
            val streamSequenceAllocator = mock<OutboxStreamSequenceAllocator>()
            whenever(
                    streamSequenceAllocator.nextSequences(
                        listOf(
                            OutboxStreamKey(
                                "local-domain",
                                "963b3779794e5b98ee843f43c56811bebc9ed53050f0861c47612b0b6b3dd089",
                            )
                        )
                    )
                )
                .thenReturn(listOf(9))
            val serializer = JacksonEventSerializer(objectMapper)
            val relaySignal = mock<OutboxRelaySignal>()
            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    eventTypeRegistry,
                    streamSequenceAllocator,
                    relaySignal,
                )

            val event =
                OrderCreatedEvent(
                    orderId = OrderId(42L),
                    merchantId = MerchantId(7),
                    payableAmount = Price.ofFen(9999),
                    currency = "CNY",
                    items = listOf(orderItemSnapshot(quantity = 2)),
                    occurredAt = Instant.parse("2025-01-01T00:00:00Z"),
                )

            publisher.publishEvent(event)

            val captor = argumentCaptor<List<OutboxEntry>>()
            verify(mockRepository, times(1)).saveAll(captor.capture())
            verify(relaySignal).signalAfterCommit()

            val saved = captor.firstValue.single()
            saved.id shouldNotBe ""
            saved.eventId shouldNotBe ""
            saved.eventType shouldBe "order.created"
            saved.eventClassName shouldBe "com.jstore.order.domain.order.event.OrderCreatedEvent"
            saved.eventVersion shouldBe 4
            saved.occurredAt shouldBe event.occurredAt
            saved.status shouldBe OutboxEntryStatus.PENDING
            saved.retryCount shouldBe 0
            saved.payload shouldNotBe ""
            saved.orderingKey shouldBe
                "963b3779794e5b98ee843f43c56811bebc9ed53050f0861c47612b0b6b3dd089"
            saved.sequenceNo shouldBe 9
        }

        test("aggregate acknowledgement runs only after transaction commit") {
            val publisher =
                OutboxEventPublisher(
                    mock(),
                    mock(),
                    SnowFlakSequence(1, 1),
                    mock(),
                    mock(),
                )
            var acknowledged = false
            TransactionSynchronizationManager.initSynchronization()

            publisher.afterPublicationCommitted { acknowledged = true }

            acknowledged shouldBe false
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            acknowledged shouldBe true
        }

        test("rollback leaves aggregate acknowledgement pending") {
            val publisher =
                OutboxEventPublisher(
                    mock(),
                    mock(),
                    SnowFlakSequence(1, 1),
                    mock(),
                    mock(),
                )
            var acknowledged = false
            TransactionSynchronizationManager.initSynchronization()

            publisher.afterPublicationCommitted { acknowledged = true }
            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            }

            acknowledged shouldBe false
        }

        test("publishEvents saves one ordered batch with allocated stream sequences") {
            val mockRepository =
                mock<OutboxEntryRepository> {
                    on { saveAll(any()) } doAnswer
                        @Suppress("UNCHECKED_CAST") { it.arguments[0] as List<OutboxEntry> }
                }
            val eventTypeRegistry =
                InMemoryEventTypeRegistry().apply {
                    register("order.created", 4, OrderCreatedEvent::class.java)
                }
            val streamSequenceAllocator = mock<OutboxStreamSequenceAllocator>()
            val orderingKey = "963b3779794e5b98ee843f43c56811bebc9ed53050f0861c47612b0b6b3dd089"
            val streams =
                listOf(
                    OutboxStreamKey(OutboxTransportIds.LOCAL_DOMAIN, orderingKey),
                    OutboxStreamKey(OutboxTransportIds.LOCAL_DOMAIN, orderingKey),
                )
            whenever(streamSequenceAllocator.nextSequences(streams)).thenReturn(listOf(9, 10))
            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    JacksonEventSerializer(objectMapper),
                    SnowFlakSequence(1, 1),
                    eventTypeRegistry,
                    streamSequenceAllocator,
                )
            val events =
                listOf(
                    orderCreatedEvent(OrderId(42L)),
                    orderCreatedEvent(OrderId(42L)),
                )

            publisher.publishEvents(events)

            val captor = argumentCaptor<List<OutboxEntry>>()
            verify(mockRepository).saveAll(captor.capture())
            verify(mockRepository, never()).save(any())
            captor.firstValue.map { it.eventId } shouldBe events.map { it.eventId }
            captor.firstValue.map { it.sequenceNo } shouldBe listOf(9L, 10L)
            captor.firstValue.map { it.createdAt }.distinct().size shouldBe 1
        }

        test("batch serialization failure does not allocate sequences or save entries") {
            val first = orderCreatedEvent(OrderId(42L))
            val second = orderCreatedEvent(OrderId(42L))
            val mockRepository = mock<OutboxEntryRepository>()
            val serializer =
                mock<EventSerializer> {
                    on { serialize(first) } doReturn "{}"
                    on { serialize(second) } doThrow RuntimeException("serialization error")
                }
            val eventTypeRegistry =
                InMemoryEventTypeRegistry().apply {
                    register("order.created", 4, OrderCreatedEvent::class.java)
                }
            val streamSequenceAllocator = mock<OutboxStreamSequenceAllocator>()
            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    eventTypeRegistry,
                    streamSequenceAllocator,
                )

            shouldThrow<RuntimeException> { publisher.publishEvents(listOf(first, second)) }

            verify(streamSequenceAllocator, never()).nextSequences(any())
            verify(mockRepository, never()).saveAll(any())
            verify(mockRepository, never()).save(any())
        }

        test("empty event batch is a no-op") {
            val mockRepository = mock<OutboxEntryRepository>()
            val serializer = mock<EventSerializer>()
            val streamSequenceAllocator = mock<OutboxStreamSequenceAllocator>()
            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    serializer,
                    SnowFlakSequence(1, 1),
                    InMemoryEventTypeRegistry(),
                    streamSequenceAllocator,
                )

            publisher.publishEvents(emptyList())

            verifyNoInteractions(mockRepository, serializer, streamSequenceAllocator)
        }

        test("batch persistence failure propagates to the caller") {
            val event = orderCreatedEvent(OrderId(42L))
            val mockRepository =
                mock<OutboxEntryRepository> {
                    on { saveAll(any()) } doThrow RuntimeException("outbox unavailable")
                }
            val eventTypeRegistry =
                InMemoryEventTypeRegistry().apply {
                    register("order.created", 4, OrderCreatedEvent::class.java)
                }
            val streamSequenceAllocator = mock<OutboxStreamSequenceAllocator>()
            whenever(streamSequenceAllocator.nextSequences(any())).thenReturn(listOf(1L))
            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    JacksonEventSerializer(objectMapper),
                    SnowFlakSequence(1, 1),
                    eventTypeRegistry,
                    streamSequenceAllocator,
                )

            shouldThrow<RuntimeException> { publisher.publishEvents(listOf(event)) }

            verify(mockRepository).saveAll(any())
        }

        test("serialization failure propagates exception ensuring business transaction rollback") {
            val mockRepository = mock<OutboxEntryRepository>()
            val mockSerializer =
                mock<EventSerializer> {
                    on { serialize(any()) } doThrow RuntimeException("serialization error")
                }
            val eventTypeRegistry =
                InMemoryEventTypeRegistry().apply {
                    register("order.created", 4, OrderCreatedEvent::class.java)
                }
            val streamSequenceAllocator = mock<OutboxStreamSequenceAllocator>()

            val publisher =
                OutboxEventPublisher(
                    mockRepository,
                    mockSerializer,
                    SnowFlakSequence(1, 1),
                    eventTypeRegistry,
                    streamSequenceAllocator,
                )

            val event =
                OrderCreatedEvent(
                    orderId = OrderId(1L),
                    merchantId = MerchantId(7),
                    payableAmount = Price.ofFen(100),
                    currency = "CNY",
                    items = listOf(orderItemSnapshot()),
                    occurredAt = Instant.now(),
                )

            shouldThrow<RuntimeException> {
                publisher.publishEvent(event)
            }

            verify(mockRepository, never()).saveAll(any())
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
                    mock(),
                )

            val event =
                OrderCreatedEvent(
                    orderId = OrderId(1L),
                    merchantId = MerchantId(7),
                    payableAmount = Price.ofFen(100),
                    currency = "CNY",
                    items = listOf(orderItemSnapshot()),
                    occurredAt = Instant.now(),
                )

            shouldThrow<OutboxSerializationException> {
                publisher.publishEvent(event)
            }

            verify(mockRepository, never()).saveAll(any())
        }
    })
