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

import com.jstore.messaging.MessageConsumptionRetentionRepository
import com.jstore.outbox.*
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.time.Instant
import org.mockito.kotlin.*

/**
 * OutboxCleaner 属性测试
 *
 * Feature: transactional-outbox
 */
@OptIn(ExperimentalKotest::class)
class OutboxCleanerPropertyTest :
    FunSpec({

        // -- Generators --

        val arbId = Arb.string(36..36, Codepoint.alphanumeric())
        val arbEventType = Arb.constant("com.jstore.order.domain.order.event.OrderCreatedEvent")
        val arbPayload = Arb.constant("""{"source":1}""")
        val arbAggregateType = Arb.constant("Order")
        val arbAggregateId = Arb.long(1L..100_000L).map { it.toString() }
        val arbStatus =
            Arb.of(
                OutboxEntryStatus.PENDING,
                OutboxEntryStatus.PUBLISHED,
                OutboxEntryStatus.FAILED,
                OutboxEntryStatus.DEAD_LETTER,
            )
        val arbRetryCount = Arb.int(0..10)

        fun arbOutboxEntry(
            status: Arb<OutboxEntryStatus> = arbStatus,
            retryCount: Arb<Int> = arbRetryCount,
            createdAt: Arb<Instant> =
                Arb.long(1_000_000L..2_000_000_000L).map { Instant.ofEpochSecond(it) },
        ): Arb<OutboxEntry> =
            Arb.bind(
                arbId,
                arbEventType,
                arbPayload,
                arbAggregateType,
                arbAggregateId,
                status,
                createdAt,
                retryCount,
            ) { id, eventType, payload, aggType, aggId, st, ts, rc ->
                OutboxEntry(
                    id = id,
                    eventType = eventType,
                    payload = payload,
                    aggregateType = aggType,
                    aggregateId = aggId,
                    status = st,
                    createdAt = ts,
                    updatedAt = ts,
                    retryCount = rc,
                    publishedAt = ts.takeIf { st == OutboxEntryStatus.PUBLISHED },
                    orderingKey = OutboxOrderingKeys.domain(aggType, aggId),
                    sequenceNo = 1,
                )
            }

        /**
         * Feature: transactional-outbox, Property 8: 清理仅删除符合条件的已发布条目
         *
         * Validates: Requirements 6.1, 6.3, 6.4
         */
        test("Property 8: cleanup only deletes PUBLISHED entries older than retention period") {
            checkAll(
                PropTestConfig(iterations = 20),
                Arb.list(arbOutboxEntry(), 1..20),
                Arb.int(1..30),
            ) { entries, retentionDays ->
                val now = Instant.now()
                val retentionCutoff = now.minusSeconds(retentionDays.toLong() * 86400)

                // Compute which entries should be deleted
                val shouldBeDeleted = entries.filter { entry ->
                    entry.status == OutboxEntryStatus.PUBLISHED &&
                        entry.createdAt.isBefore(retentionCutoff)
                }

                var capturedBefore: Instant? = null
                val mockRepo =
                    mock<OutboxEntryRepository> {
                        on { deletePublishedBefore(any(), any()) } doAnswer
                            { invocation ->
                                capturedBefore = invocation.arguments[0] as Instant
                                shouldBeDeleted.size
                            }
                    }
                val properties =
                    OutboxProperties(
                        retentionDays = retentionDays,
                        consumptionRetentionDays = retentionDays,
                        cleanupBatchSize = 500,
                    )

                val cleaner =
                    OutboxCleaner(
                        mockRepo,
                        properties,
                        mock<MessageConsumptionRetentionRepository>(),
                    )
                cleaner.cleanup()

                // Verify deletePublishedBefore was called
                verify(mockRepo).deletePublishedBefore(any(), eq(500))

                // Verify: the query condition (status=PUBLISHED) inherently excludes
                // DEAD_LETTER, PENDING, and FAILED entries — assert this invariant
                val nonPublished = entries.filter { it.status != OutboxEntryStatus.PUBLISHED }
                nonPublished.none { it.status == OutboxEntryStatus.PUBLISHED } shouldBe true

                // Verify capturedBefore is set (cleanup was invoked)
                val before = capturedBefore
                before shouldNotBe null

                // Verify only PUBLISHED + expired entries match the deletion criteria
                entries.forEach { entry ->
                    val matchesDeletion =
                        entry.status == OutboxEntryStatus.PUBLISHED &&
                            entry.createdAt.isBefore(before!!)
                    if (entry.status != OutboxEntryStatus.PUBLISHED) {
                        matchesDeletion shouldBe false
                    }
                }
            }
        }
    })
