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

/**
 * Accepts an ordered batch into the caller's business transaction.
 *
 * Return means staged, not committed or delivered. All messages and allocated sequences must commit
 * or roll back with business state. An empty batch is a no-op. Failures must propagate.
 * Implementations must not send to an external transport from this call.
 */
fun interface OutboxWriter {
    fun append(messages: List<OutboxMessage>)
}

/** One deployment-selected implementation owns both transactional append and stream allocation. */
data class OutboxBackend(
    val id: String,
    val writer: OutboxWriter,
    val sequenceAllocator: OutboxStreamSequenceAllocator,
) {
    init {
        require(id.isNotBlank()) { "Outbox backend ID must not be blank" }
    }
}
