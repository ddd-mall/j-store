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

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.LocalDomainEventBus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Instant
import org.mockito.kotlin.*

/**
 * OutboxPublisher 属性测试
 *
 * Feature: transactional-outbox
 */
class OutboxPublisherPropertyTest :
    FunSpec({

        // -- Generators --

        val arbId = Arb.string(36..36, Codepoint.alphanumeric())
        val arbEventType = Arb.constant("com.jstore.order.domain.order.event.OrderCreatedEvent")
        val arbPayload = Arb.constant("""{"source":1}""")
        val arbAggregateType = Arb.constant("Order")
        val arbAggregateId = Arb.long(1L..100_000L).map { it.toString() }
        val arbInstant = Arb.long(1_000_000L..2_000_000_000L).map { Instant.ofEpochSecond(it) }

        fun arbOutboxEntry(
            status: OutboxEntryStatus = OutboxEntryStatus.PENDING,
            retryCount: Int = 0,
            createdAt: Arb<Instant> = arbInstant,
        ): Arb<OutboxEntry> =
            Arb.bind(
                arbId,
                arbEventType,
                arbPayload,
                arbAggregateType,
                arbAggregateId,
                createdAt,
            ) { id, eventType, payload, aggType, aggId, ts ->
                OutboxEntry(
                    id = id,
                    eventType = eventType,
                    payload = payload,
                    aggregateType = aggType,
                    aggregateId = aggId,
                    status = status,
                    createdAt = ts,
                    updatedAt = ts,
                    retryCount = retryCount,
                )
            }

        // Stub DomainEvent for testing
        data class StubEvent(override val source: Any = "stub") : DomainEvent

        /**
         * Feature: transactional-outbox, Property 3: 成功投递后状态变更为 PUBLISHED
         *
         * Validates: Requirements 2.2, 2.3
         */
        test("Property 3: successful delivery updates status to PUBLISHED") {
            checkAll(PropTestConfig(iterations = 20), arbOutboxEntry()) { entry ->
                val savedEntries = mutableListOf<OutboxEntry>()
                val mockRepo =
                    mock<OutboxEntryRepository> {
                        on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                            listOf(entry)
                        on { markPublished(any(), any()) } doAnswer
                            { invocation ->
                                val saved = invocation.arguments[0] as OutboxEntry
                                savedEntries.add(saved)
                                true
                            }
                    }
                val mockSerializer =
                    mock<EventSerializer> {
                        on { deserialize(any(), any(), any()) } doReturn StubEvent()
                    }
                val mockBus = mock<LocalDomainEventBus>()
                val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

                val publisher =
                    OutboxPublisher(mockRepo, deliveryRouter(mockSerializer, mockBus), properties)
                publisher.pollAndPublish()

                savedEntries.size shouldBe 1
                savedEntries[0].status shouldBe OutboxEntryStatus.PUBLISHED
                savedEntries[0].id shouldBe entry.id
            }
        }

        /**
         * Feature: transactional-outbox, Property 4: 事件按创建时间升序投递
         *
         * Validates: Requirements 2.4
         */
        test("Property 4: events are delivered in createdAt ascending order") {
            checkAll(PropTestConfig(iterations = 20), Arb.list(arbOutboxEntry(), 2..10)) { entries
                ->
                // Sort entries by createdAt ascending (simulating what the repository query does)
                val sortedEntries = entries.sortedBy { it.createdAt }

                val mockRepo =
                    mock<OutboxEntryRepository> {
                        on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                            sortedEntries
                        on { markPublished(any(), any()) } doReturn true
                        on { markFailed(any(), any()) } doReturn true
                    }
                val mockSerializer =
                    mock<EventSerializer> {
                        on { deserialize(any(), any(), any()) } doReturn StubEvent()
                    }
                val mockBus =
                    mock<LocalDomainEventBus> {
                        on { publishEvent(any()) } doAnswer
                            {
                                // We track order via the save calls instead
                                Unit
                            }
                    }
                val properties = OutboxProperties(maxRetryCount = 5, batchSize = entries.size + 10)

                // Track delivery order via save calls
                val savedOrder = mutableListOf<String>()
                whenever(mockRepo.markPublished(any(), any())).doAnswer { invocation ->
                    val saved = invocation.arguments[0] as OutboxEntry
                    savedOrder.add(saved.id)
                    true
                }
                whenever(mockRepo.markFailed(any(), any())).doAnswer { invocation ->
                    val saved = invocation.arguments[0] as OutboxEntry
                    savedOrder.add(saved.id)
                    true
                }

                val publisher =
                    OutboxPublisher(mockRepo, deliveryRouter(mockSerializer, mockBus), properties)
                publisher.pollAndPublish()

                // Verify delivery order matches createdAt ascending
                savedOrder shouldBe sortedEntries.map { it.id }
            }
        }

        /**
         * Feature: transactional-outbox, Property 5: 批次大小限制
         *
         * Validates: Requirements 2.5
         */
        test("Property 5: batch size limits the number of entries per poll") {
            checkAll(
                PropTestConfig(iterations = 20),
                Arb.int(1..10),
                Arb.int(11..30),
            ) { batchSize, totalEntries ->
                // The repository should respect batchSize and return at most batchSize entries
                val allEntries =
                    (1..totalEntries).map { i ->
                        OutboxEntry(
                            id = "id-$i",
                            eventType = "com.jstore.order.domain.order.event.OrderCreatedEvent",
                            payload = """{"source":1}""",
                            aggregateType = "Order",
                            aggregateId = "$i",
                            status = OutboxEntryStatus.PENDING,
                            createdAt = Instant.ofEpochSecond(1_000_000L + i),
                            updatedAt = Instant.ofEpochSecond(1_000_000L + i),
                            retryCount = 0,
                        )
                    }
                // Simulate repository returning at most batchSize entries
                val returnedEntries = allEntries.take(batchSize)

                var capturedBatchSize = 0
                val mockRepo =
                    mock<OutboxEntryRepository> {
                        on { claimPendingAndRetryable(any(), any(), any(), any()) } doAnswer
                            { invocation ->
                                capturedBatchSize = invocation.arguments[1] as Int
                                returnedEntries
                            }
                        on { markPublished(any(), any()) } doReturn true
                    }
                val mockSerializer =
                    mock<EventSerializer> {
                        on { deserialize(any(), any(), any()) } doReturn StubEvent()
                    }
                val mockBus = mock<LocalDomainEventBus>()
                val properties = OutboxProperties(maxRetryCount = 5, batchSize = batchSize)

                val publisher =
                    OutboxPublisher(mockRepo, deliveryRouter(mockSerializer, mockBus), properties)
                publisher.pollAndPublish()

                // Verify OutboxPublisher passes batchSize to repository
                capturedBatchSize shouldBe batchSize
                // Verify we processed at most batchSize entries
                verify(mockBus, atMost(batchSize)).publishEvent(any())
            }
        }

        /**
         * Feature: transactional-outbox, Property 6: 失败处理与死信转换
         *
         * Validates: Requirements 3.1, 3.3
         */
        test("Property 6: failure handling and dead letter conversion") {
            checkAll(
                PropTestConfig(iterations = 20),
                Arb.int(0..10),
                Arb.int(1..10),
            ) { retryCount, maxRetryCount ->
                val entry =
                    OutboxEntry(
                        id = "test-id",
                        eventType = "com.jstore.order.domain.order.event.OrderCreatedEvent",
                        payload = """{"source":1}""",
                        aggregateType = "Order",
                        aggregateId = "1",
                        status =
                            if (retryCount == 0) OutboxEntryStatus.PENDING
                            else OutboxEntryStatus.FAILED,
                        createdAt = Instant.ofEpochSecond(1_000_000L),
                        updatedAt = Instant.ofEpochSecond(1_000_000L),
                        retryCount = retryCount,
                    )

                val savedEntries = mutableListOf<OutboxEntry>()
                val mockRepo =
                    mock<OutboxEntryRepository> {
                        on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                            listOf(entry)
                        on { markFailed(any(), any()) } doAnswer
                            { invocation ->
                                val saved = invocation.arguments[0] as OutboxEntry
                                savedEntries.add(saved)
                                true
                            }
                    }
                val mockSerializer =
                    mock<EventSerializer> {
                        on { deserialize(any(), any(), any()) } doReturn StubEvent()
                    }
                // Mock bus to throw exception on delivery
                val mockBus =
                    mock<LocalDomainEventBus> {
                        on { publishEvent(any()) } doThrow RuntimeException("delivery failed")
                    }
                val properties = OutboxProperties(maxRetryCount = maxRetryCount, batchSize = 100)

                val publisher =
                    OutboxPublisher(mockRepo, deliveryRouter(mockSerializer, mockBus), properties)
                publisher.pollAndPublish()

                savedEntries.size shouldBe 1
                val saved = savedEntries[0]
                saved.retryCount shouldBe retryCount

                val expectedStatus =
                    if (retryCount >= maxRetryCount) OutboxEntryStatus.DEAD_LETTER
                    else OutboxEntryStatus.FAILED
                saved.status shouldBe expectedStatus
            }
        }
    })

private fun deliveryRouter(
    serializer: EventSerializer,
    bus: LocalDomainEventBus,
): OutboxDeliveryRouter =
    OutboxDeliveryRouter(listOf(LocalDomainEventDeliveryChannel(serializer, bus)))
