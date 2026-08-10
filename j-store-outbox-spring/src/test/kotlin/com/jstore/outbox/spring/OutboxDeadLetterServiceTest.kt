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

import com.jstore.outbox.*
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class OutboxDeadLetterServiceTest {
    @Test
    fun `requeue delegates operator reason and target ids to atomic operations port`() {
        val repository = FakeOperationsRepository()
        val service = OutboxDeadLetterService(repository)
        val at = Instant.parse("2026-08-04T01:02:03Z")

        val result =
            service.requeue(listOf("entry-1", "entry-2"), "user-7", "dependency recovered", at)

        assertEquals(2, result.requeuedCount)
        assertEquals(
            RequeueCommand(listOf("entry-1", "entry-2"), "user-7", "dependency recovered", at),
            repository.command,
        )
    }

    @Test
    fun `requeue rejects blank reason before accessing persistence`() {
        val repository = FakeOperationsRepository()
        val service = OutboxDeadLetterService(repository)

        assertFailsWith<IllegalArgumentException> {
            service.requeue(listOf("entry-1"), "user-7", "  ", Instant.now())
        }
        assertEquals(null, repository.command)
    }

    private data class RequeueCommand(
        val ids: List<String>,
        val operatorId: String,
        val reason: String,
        val nextAttemptAt: Instant,
    )

    private class FakeOperationsRepository :
        OutboxEntryRepository, OutboxDeadLetterOperationsRepository {
        var command: RequeueCommand? = null

        override fun findDeadLetters(page: Int, size: Int) =
            OutboxDeadLetterPage(emptyList(), page, size, 0)

        override fun requeueDeadLetters(
            ids: Collection<String>,
            operatorId: String,
            reason: String,
            nextAttemptAt: Instant,
        ): DeadLetterRequeueResult {
            command = RequeueCommand(ids.toList(), operatorId, reason, nextAttemptAt)
            return DeadLetterRequeueResult(ids.size, 0)
        }

        override fun save(entry: OutboxEntry) = entry

        override fun findPendingAndRetryable(maxRetryCount: Int, batchSize: Int) =
            emptyList<OutboxEntry>()

        override fun claimPendingAndRetryable(
            maxRetryCount: Int,
            batchSize: Int,
            lockedBy: String,
            lockedUntil: Instant,
        ) = emptyList<OutboxEntry>()

        override fun renewLease(
            id: String,
            lockedBy: String,
            lockToken: Long,
            lockedUntil: Instant,
        ) = false

        override fun markPublished(entry: OutboxEntry, lockedBy: String) = false

        override fun markFailed(entry: OutboxEntry, lockedBy: String) = false

        override fun findDeadLetters(batchSize: Int) = emptyList<OutboxEntry>()

        override fun requeueDeadLetters(ids: Collection<String>, nextAttemptAt: Instant) = 0

        override fun countByStatus(status: OutboxEntryStatus) = 0L

        override fun findOldestReadyAt(now: Instant, maxRetryCount: Int): Instant? = null

        override fun countExpiredLocks(now: Instant) = 0L

        override fun deletePublishedBefore(before: Instant, batchSize: Int) = 0
    }
}
