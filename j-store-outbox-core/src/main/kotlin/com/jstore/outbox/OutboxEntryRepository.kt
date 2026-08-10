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
package com.jstore.outbox

import java.time.Instant

/**
 * Outbox 条目仓储接口。
 *
 * 方法签名仅使用领域对象，不依赖任何框架类型。
 */
interface OutboxEntryRepository {

    fun save(entry: OutboxEntry): OutboxEntry

    fun claimPendingAndRetryable(
        maxRetryCount: Int,
        batchSize: Int,
        lockedBy: String,
        lockedUntil: Instant,
    ): List<OutboxEntry>

    fun renewLease(id: String, lockedBy: String, lockToken: Long, lockedUntil: Instant): Boolean

    fun markPublished(entry: OutboxEntry, lockedBy: String): Boolean

    fun markFailed(entry: OutboxEntry, lockedBy: String): Boolean

    fun findDeadLetters(batchSize: Int): List<OutboxEntry>

    fun countByStatus(status: OutboxEntryStatus): Long

    fun countByStatus(status: OutboxEntryStatus, transportId: String): Long

    fun findOldestReadyAt(now: Instant, maxRetryCount: Int): Instant?

    fun findOldestReadyAt(now: Instant, maxRetryCount: Int, transportId: String): Instant?

    fun countExpiredLocks(now: Instant): Long

    fun countExpiredLocks(now: Instant, transportId: String): Long

    fun findTransportIds(): Set<String>

    fun deletePublishedBefore(before: Instant, batchSize: Int): Int
}
