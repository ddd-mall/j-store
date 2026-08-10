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
package com.jstore.outbox.operations

import com.jstore.outbox.spring.DeadLetterRequeueResult
import com.jstore.outbox.spring.OutboxDeadLetterPage
import com.jstore.outbox.spring.OutboxDeadLetterSummary
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class RequeueDeadLettersRequest(
    @field:NotEmpty @field:Size(max = 100) val ids: List<@NotBlank String>,
    @field:NotBlank @field:Size(max = 500) val reason: String,
)

data class DeadLetterPageResponse(
    val entries: List<DeadLetterSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    companion object {
        fun from(page: OutboxDeadLetterPage) =
            DeadLetterPageResponse(
                entries = page.entries.map(DeadLetterSummaryResponse::from),
                page = page.page,
                size = page.size,
                totalElements = page.totalElements,
            )
    }
}

/** Payload is intentionally absent from the operations API contract. */
data class DeadLetterSummaryResponse(
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
) {
    companion object {
        fun from(entry: OutboxDeadLetterSummary) =
            DeadLetterSummaryResponse(
                id = entry.id,
                eventId = entry.eventId,
                eventType = entry.eventType,
                aggregateType = entry.aggregateType,
                aggregateId = entry.aggregateId,
                eventVersion = entry.eventVersion,
                occurredAt = entry.occurredAt,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                retryCount = entry.retryCount,
                lastError = entry.lastError,
                transportId = entry.transportId,
                orderingKey = entry.orderingKey,
                sequenceNo = entry.sequenceNo,
            )
    }
}

data class RequeueDeadLettersResponse(
    val requeuedCount: Int,
    val notRequeuedCount: Int,
) {
    companion object {
        fun from(result: DeadLetterRequeueResult) =
            RequeueDeadLettersResponse(result.requeuedCount, result.notRequeuedCount)
    }
}

data class OutboxOperationsErrorResponse(
    val message: String,
    val errorCode: String,
)
