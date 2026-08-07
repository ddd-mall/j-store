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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
        test("cleanup calls deletePublishedBefore with correct retention cutoff and batch size") {
            var capturedBefore: Instant? = null
            var capturedBatchSize: Int? = null
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { deletePublishedBefore(any(), any()) } doAnswer
                        { invocation ->
                            capturedBefore = invocation.arguments[0] as Instant
                            capturedBatchSize = invocation.arguments[1] as Int
                            10
                        }
                }
            val properties = OutboxProperties(retentionDays = 7, cleanupBatchSize = 500)

            val cleaner = OutboxCleaner(mockRepo, properties)
            val beforeCleanup = Instant.now()
            cleaner.cleanup()

            capturedBefore shouldNotBe null
            capturedBatchSize shouldBe 500

            // The cutoff should be approximately 7 days ago
            val expectedCutoff = beforeCleanup.minus(7, ChronoUnit.DAYS)
            val diffSeconds =
                kotlin.math.abs(capturedBefore!!.epochSecond - expectedCutoff.epochSecond)
            // Allow 2 seconds tolerance for test execution time
            (diffSeconds < 2) shouldBe true
        }

        test("cleanup does not throw when repository throws exception") {
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { deletePublishedBefore(any(), any()) } doThrow RuntimeException("DB error")
                }
            val properties = OutboxProperties(retentionDays = 7, cleanupBatchSize = 500)

            val cleaner = OutboxCleaner(mockRepo, properties)

            // Should NOT throw — top-level catch prevents interruption
            cleaner.cleanup()
        }

        test("cleanup with zero deleted entries completes successfully") {
            val mockRepo =
                mock<OutboxEntryRepository> {
                    on { deletePublishedBefore(any(), any()) } doReturn 0
                }
            val properties = OutboxProperties(retentionDays = 7, cleanupBatchSize = 500)

            val cleaner = OutboxCleaner(mockRepo, properties)
            cleaner.cleanup()

            verify(mockRepo).deletePublishedBefore(any(), eq(500))
        }
    })
