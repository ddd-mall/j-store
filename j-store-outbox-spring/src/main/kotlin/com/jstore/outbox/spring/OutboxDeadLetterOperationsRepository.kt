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

import java.time.Instant

interface OutboxDeadLetterOperations {
    fun findDeadLetters(page: Int, size: Int): OutboxDeadLetterPage

    fun requeue(
        ids: Collection<String>,
        operatorId: String,
        reason: String,
        nextAttemptAt: Instant = Instant.now(),
    ): DeadLetterRequeueResult
}

/**
 * Atomic persistence boundary for dead-letter operations.
 *
 * Implementations must update an entry and append its audit record in the same transaction.
 */
interface OutboxDeadLetterOperationsRepository {
    fun findDeadLetters(page: Int, size: Int): OutboxDeadLetterPage

    fun requeueDeadLetters(
        ids: Collection<String>,
        operatorId: String,
        reason: String,
        nextAttemptAt: Instant,
    ): DeadLetterRequeueResult
}

data class OutboxDeadLetterPage(
    val entries: List<OutboxDeadLetterSummary>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
)

/** Deliberately excludes payload. */
data class OutboxDeadLetterSummary(
    val id: String,
    val eventId: String,
    val eventType: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventVersion: Int,
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val retryCount: Int,
    val lastError: String?,
    val transportId: String,
    val orderingKey: String,
    val sequenceNo: Long,
)

data class DeadLetterRequeueResult(
    val requeuedCount: Int,
    val notRequeuedCount: Int,
)

enum class OutboxDeadLetterAuditAction {
    REQUEUE
}

enum class OutboxDeadLetterAuditResult {
    REQUEUED,
    NOT_REQUEUED,
}

data class OutboxDeadLetterAudit(
    val entryId: String,
    val eventId: String?,
    val operatorId: String,
    val action: OutboxDeadLetterAuditAction,
    val reason: String,
    val result: OutboxDeadLetterAuditResult,
    val createdAt: Instant,
)
