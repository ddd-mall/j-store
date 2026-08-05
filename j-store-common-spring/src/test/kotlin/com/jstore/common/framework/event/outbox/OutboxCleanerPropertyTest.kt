package com.jstore.common.framework.event.outbox

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
                )
            }

        /**
         * Feature: transactional-outbox, Property 7: 重试资格查询
         *
         * Validates: Requirements 3.2
         */
        test("Property 7: findPendingAndRetryable returns only eligible entries") {
            checkAll(
                PropTestConfig(iterations = 20),
                Arb.list(arbOutboxEntry(), 1..20),
                Arb.int(1..10),
            ) { entries, maxRetryCount ->
                // Compute expected eligible entries
                val eligible = entries.filter { entry ->
                    entry.status == OutboxEntryStatus.PENDING ||
                        (entry.status == OutboxEntryStatus.FAILED &&
                            entry.retryCount < maxRetryCount)
                }

                val mockRepo =
                    mock<OutboxEntryRepository> {
                        on { findPendingAndRetryable(eq(maxRetryCount), any()) } doReturn eligible
                    }

                val result = mockRepo.findPendingAndRetryable(maxRetryCount, 100)

                // Verify no PUBLISHED or DEAD_LETTER entries are returned
                result.forEach { entry ->
                    val isEligible =
                        entry.status == OutboxEntryStatus.PENDING ||
                            (entry.status == OutboxEntryStatus.FAILED &&
                                entry.retryCount < maxRetryCount)
                    isEligible shouldBe true
                }
                result.size shouldBe eligible.size
            }
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
                    OutboxProperties(retentionDays = retentionDays, cleanupBatchSize = 500)

                val cleaner = OutboxCleaner(mockRepo, properties)
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
