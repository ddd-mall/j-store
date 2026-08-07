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
import com.jstore.common.framework.event.DomainEventBus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import org.mockito.kotlin.*

/**
 * OutboxPublisher 单元测试
 *
 * Validates: Requirements 2.1, 2.2, 2.3, 3.1, 3.5, 7.1, 7.2, 7.3
 */
class OutboxPublisherTest :
    FunSpec({
        data class StubEvent(override val source: Any = "stub") : DomainEvent

        class RecordingTransactionOperations : OutboxRelayTransactionOperations {
            val calls = mutableListOf<String>()

            override fun <T> executeDelivery(action: () -> T): T {
                calls.add("delivery-begin")
                try {
                    return action().also {
                        calls.add("delivery-commit")
                    }
                } catch (e: Exception) {
                    calls.add("delivery-rollback")
                    throw e
                }
            }

            override fun <T> executeFailure(action: () -> T): T {
                calls.add("failure-begin")
                return action().also {
                    calls.add("failure-commit")
                }
            }
        }

        fun createEntry(
            id: String = "entry-1",
            retryCount: Int = 0,
            status: OutboxEntryStatus = OutboxEntryStatus.PENDING,
        ) =
            OutboxEntry(
                id = id,
                eventType = "com.example.StubEvent",
                payload = """{"source":"stub"}""",
                aggregateType = "Order",
                aggregateId = "42",
                status = status,
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2025-01-01T00:00:00Z"),
                retryCount = retryCount,
            )

        test("poll, deliver, and update status to PUBLISHED on success") {
            val entry = createEntry()
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                        listOf(entry)
                    on { markPublished(any(), any()) } doReturn true
                }
            val mockSerializer =
                mock<EventSerializer> {
                    on { deserialize(any(), any(), any()) } doReturn StubEvent()
                }
            val mockBus = mock<DomainEventBus>()
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
            publisher.pollAndPublish()

            verify(mockBus).publishEvent(any())
            val captor = argumentCaptor<OutboxEntry>()
            verify(mockRepo).markPublished(captor.capture(), any())
            captor.firstValue.status shouldBe OutboxEntryStatus.PUBLISHED
            captor.firstValue.lockedBy shouldBe null
            captor.firstValue.lockedUntil shouldBe null
            captor.firstValue.lastError shouldBe null
        }

        test("delivery failure increments retryCount and sets FAILED status") {
            val entry = createEntry(retryCount = 2)
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                        listOf(entry)
                    on { markFailed(any(), any()) } doReturn true
                }
            val mockSerializer =
                mock<EventSerializer> {
                    on { deserialize(any(), any(), any()) } doReturn StubEvent()
                }
            val mockBus =
                mock<DomainEventBus> {
                    on { publishEvent(any()) } doThrow RuntimeException("bus error")
                }
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
            publisher.pollAndPublish()

            val captor = argumentCaptor<OutboxEntry>()
            verify(mockRepo).markFailed(captor.capture(), any())
            captor.firstValue.retryCount shouldBe 2
            captor.firstValue.status shouldBe OutboxEntryStatus.FAILED
            captor.firstValue.lockedBy shouldBe null
            captor.firstValue.lockedUntil shouldBe null
            captor.firstValue.lastError shouldBe "java.lang.RuntimeException: bus error"
            captor.firstValue.nextAttemptAt.isAfter(Instant.now()) shouldBe true
        }

        test("delivery failure at max retry sets DEAD_LETTER status") {
            val entry = createEntry(retryCount = 5)
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                        listOf(entry)
                    on { markFailed(any(), any()) } doReturn true
                }
            val mockSerializer =
                mock<EventSerializer> {
                    on { deserialize(any(), any(), any()) } doReturn StubEvent()
                }
            val mockBus =
                mock<DomainEventBus> {
                    on { publishEvent(any()) } doThrow RuntimeException("bus error")
                }
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
            publisher.pollAndPublish()

            val captor = argumentCaptor<OutboxEntry>()
            verify(mockRepo).markFailed(captor.capture(), any())
            captor.firstValue.retryCount shouldBe 5
            captor.firstValue.status shouldBe OutboxEntryStatus.DEAD_LETTER
            captor.firstValue.lockedBy shouldBe null
            captor.firstValue.lockedUntil shouldBe null
        }

        test("top-level exception is propagated so scheduler health records failure") {
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doThrow
                        RuntimeException("DB connection lost")
                }
            val mockSerializer = mock<EventSerializer>()
            val mockBus = mock<DomainEventBus>()
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)

            shouldThrow<RuntimeException> { publisher.pollAndPublish() }

            // Verify no delivery was attempted
            verify(mockBus, never()).publishEvent(any())
        }

        test("poll claims entries with worker id and lock timeout") {
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn emptyList()
                }
            val mockSerializer = mock<EventSerializer>()
            val mockBus = mock<DomainEventBus>()
            val properties =
                OutboxProperties(
                    maxRetryCount = 5,
                    batchSize = 12,
                    lockTimeoutMillis = 30000,
                    workerId = "worker-a",
                )

            val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
            val before = Instant.now()
            publisher.pollAndPublish()

            verify(mockRepo)
                .claimPendingAndRetryable(
                    eq(5),
                    eq(12),
                    eq("worker-a"),
                    argThat { isAfter(before.plusMillis(29000)) },
                )
        }

        test("empty poll results in no delivery attempts") {
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn emptyList()
                }
            val mockSerializer = mock<EventSerializer>()
            val mockBus = mock<DomainEventBus>()
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
            publisher.pollAndPublish()

            verify(mockBus, never()).publishEvent(any())
            verify(mockRepo, never()).markPublished(any(), any())
            verify(mockRepo, never()).markFailed(any(), any())
        }

        test("multiple entries: one failure does not prevent others from being delivered") {
            val entry1 = createEntry(id = "entry-1")
            val entry2 = createEntry(id = "entry-2")
            val entry3 = createEntry(id = "entry-3")

            val stubEvent = StubEvent()
            var callCount = 0
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                        listOf(entry1, entry2, entry3)
                    on { markPublished(any(), any()) } doReturn true
                    on { markFailed(any(), any()) } doReturn true
                }
            val mockSerializer =
                mock<EventSerializer> {
                    on { deserialize(any(), any(), any()) } doReturn stubEvent
                }
            val mockBus =
                mock<DomainEventBus> {
                    on { publishEvent(any()) } doAnswer
                        {
                            callCount++
                            if (callCount == 2) throw RuntimeException("second delivery fails")
                        }
                }
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher = OutboxPublisher(mockRepo, mockSerializer, mockBus, properties)
            publisher.pollAndPublish()

            verify(mockRepo, times(2)).markPublished(any(), any())
            verify(mockRepo, times(1)).markFailed(any(), any())
        }

        test(
            "single entry delivery runs in delivery transaction and failure update runs in separate transaction"
        ) {
            val entry = createEntry()
            val transactions = RecordingTransactionOperations()
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                        listOf(entry)
                    on { markFailed(any(), any()) } doReturn true
                }
            val mockSerializer =
                mock<EventSerializer> {
                    on { deserialize(any(), any(), any()) } doReturn StubEvent()
                }
            val mockBus =
                mock<DomainEventBus> {
                    on { publishEvent(any()) } doThrow RuntimeException("bus error")
                }
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher =
                OutboxPublisher(
                    mockRepo,
                    mockSerializer,
                    mockBus,
                    properties,
                    transactionOperations = transactions,
                )
            publisher.pollAndPublish()

            transactions.calls shouldBe
                listOf(
                    "delivery-begin",
                    "delivery-rollback",
                    "failure-begin",
                    "failure-commit",
                )
            verify(mockRepo).markFailed(any(), any())
        }

        test("publish lock ownership change rolls back delivery and records failure separately") {
            val entry = createEntry()
            val transactions = RecordingTransactionOperations()
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { claimPendingAndRetryable(any(), any(), any(), any()) } doReturn
                        listOf(entry)
                    on { markPublished(any(), any()) } doReturn false
                    on { markFailed(any(), any()) } doReturn false
                }
            val mockSerializer =
                mock<EventSerializer> {
                    on { deserialize(any(), any(), any()) } doReturn StubEvent()
                }
            val mockBus = mock<DomainEventBus>()
            val properties = OutboxProperties(maxRetryCount = 5, batchSize = 100)

            val publisher =
                OutboxPublisher(
                    mockRepo,
                    mockSerializer,
                    mockBus,
                    properties,
                    transactionOperations = transactions,
                )
            publisher.pollAndPublish()

            transactions.calls shouldBe
                listOf(
                    "delivery-begin",
                    "delivery-rollback",
                    "failure-begin",
                    "failure-commit",
                )
            verify(mockBus).publishEvent(any())
            verify(mockRepo).markFailed(any(), any())
        }
    })
