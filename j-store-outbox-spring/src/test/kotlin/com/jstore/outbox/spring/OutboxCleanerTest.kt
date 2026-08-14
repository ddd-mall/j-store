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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.mockito.kotlin.*

/**
 * OutboxCleaner 单元测试
 *
 * Validates: Requirements 6.1, 6.4
 */
class OutboxCleanerTest :
    FunSpec({
        test("cleanup drains multiple batches until a partial batch is returned") {
            val mockRepo = mock<OutboxEntryRepository>()
            whenever(mockRepo.deletePublishedBefore(any(), eq(500))).thenReturn(500, 500, 10)
            val properties =
                OutboxProperties(
                    retentionDays = 7,
                    cleanupBatchSize = 500,
                    cleanupMaxBatchesPerRun = 10,
                )

            val cleaner = OutboxCleaner(mockRepo, properties, mock())
            val beforeCleanup = Instant.now()
            cleaner.cleanup()

            val cutoffCaptor = argumentCaptor<Instant>()
            verify(mockRepo, times(3)).deletePublishedBefore(cutoffCaptor.capture(), eq(500))
            val capturedBefore = cutoffCaptor.firstValue

            // The cutoff should be approximately 7 days ago
            val expectedCutoff = beforeCleanup.minus(7, ChronoUnit.DAYS)
            val diffSeconds =
                kotlin.math.abs(capturedBefore.epochSecond - expectedCutoff.epochSecond)
            // Allow 2 seconds tolerance for test execution time
            (diffSeconds < 2) shouldBe true
        }

        test("cleanup stops at the configured batch budget") {
            val repository =
                mock<OutboxEntryRepository> {
                    on { deletePublishedBefore(any(), any()) } doReturn 500
                }
            val properties = OutboxProperties(cleanupBatchSize = 500, cleanupMaxBatchesPerRun = 3)

            OutboxCleaner(repository, properties, mock()).cleanup()

            verify(repository, times(3)).deletePublishedBefore(any(), eq(500))
        }

        test("cleanup advances consumption details and inactive stream positions") {
            val outboxRepository =
                mock<OutboxEntryRepository> {
                    on { deletePublishedBefore(any(), any()) } doReturn 0
                }
            val retentionRepository = mock<MessageConsumptionRetentionRepository>()
            whenever(retentionRepository.deleteConsumptionsBefore(any(), eq(500)))
                .thenReturn(500, 1)
            whenever(retentionRepository.deleteInactiveStreamPositionsBefore(any(), eq(500)))
                .thenReturn(0)

            OutboxCleaner(
                    outboxRepository,
                    OutboxProperties(cleanupBatchSize = 500),
                    retentionRepository,
                )
                .cleanup()

            verify(retentionRepository, times(2)).deleteConsumptionsBefore(any(), eq(500))
            verify(retentionRepository).deleteInactiveStreamPositionsBefore(any(), eq(500))
        }

        test("cleanup does not throw when repository throws exception") {
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { deletePublishedBefore(any(), any()) } doThrow RuntimeException("DB error")
                }
            val properties = OutboxProperties(retentionDays = 7, cleanupBatchSize = 500)

            val cleaner = OutboxCleaner(mockRepo, properties, mock())

            // Should NOT throw — top-level catch prevents interruption
            cleaner.cleanup()
        }

        test("cleanup with zero deleted entries completes successfully") {
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { deletePublishedBefore(any(), any()) } doReturn 0
                }
            val properties = OutboxProperties(retentionDays = 7, cleanupBatchSize = 500)

            val cleaner = OutboxCleaner(mockRepo, properties, mock())
            cleaner.cleanup()

            verify(mockRepo).deletePublishedBefore(any(), eq(500))
        }
    })
