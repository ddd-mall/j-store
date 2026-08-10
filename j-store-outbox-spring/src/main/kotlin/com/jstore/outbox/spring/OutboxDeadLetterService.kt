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
import org.slf4j.LoggerFactory

class OutboxDeadLetterService(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val outboxMonitor: OutboxMonitor = NoopOutboxMonitor,
) : OutboxDeadLetterOperations {
    private val logger = LoggerFactory.getLogger(OutboxDeadLetterService::class.java)

    fun findDeadLetters(batchSize: Int): List<OutboxEntry> {
        return outboxEntryRepository.findDeadLetters(batchSize)
    }

    override fun findDeadLetters(page: Int, size: Int): OutboxDeadLetterPage {
        require(page >= 1) { "page must be at least 1" }
        require(size in 1..200) { "size must be between 1 and 200" }
        return operationsRepository().findDeadLetters(page, size)
    }

    override fun requeue(
        ids: Collection<String>,
        operatorId: String,
        reason: String,
        nextAttemptAt: Instant,
    ): DeadLetterRequeueResult {
        require(ids.isNotEmpty()) { "ids must not be empty" }
        require(ids.size <= 100) { "at most 100 dead letters can be requeued at once" }
        require(operatorId.isNotBlank()) { "operatorId must not be blank" }
        require(reason.isNotBlank()) { "reason must not be blank" }

        val result =
            operationsRepository()
                .requeueDeadLetters(
                    ids = ids.toList().distinct(),
                    operatorId = operatorId,
                    reason = reason.trim(),
                    nextAttemptAt = nextAttemptAt,
                )
        if (result.requeuedCount > 0) {
            logger.warn(
                "Outbox dead letters requeued: count={}, requestedCount={}, operatorId={}",
                result.requeuedCount,
                ids.size,
                operatorId,
            )
            outboxMonitor.recordRequeue(result.requeuedCount)
        }
        return result
    }

    fun countDeadLetters(): Long {
        return outboxEntryRepository.countByStatus(OutboxEntryStatus.DEAD_LETTER)
    }

    private fun operationsRepository(): OutboxDeadLetterOperationsRepository {
        return outboxEntryRepository as? OutboxDeadLetterOperationsRepository
            ?: error("Outbox repository does not provide production dead-letter operations")
    }
}
