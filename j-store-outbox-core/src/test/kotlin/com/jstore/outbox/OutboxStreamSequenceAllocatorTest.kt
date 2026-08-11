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

import kotlin.test.Test
import kotlin.test.assertEquals

class OutboxStreamSequenceAllocatorTest {
    @Test
    fun `default batch allocation preserves input order through single allocations`() {
        val calls = mutableListOf<OutboxStreamKey>()
        val allocator = OutboxStreamSequenceAllocator { transportId, orderingKey ->
            calls += OutboxStreamKey(transportId, orderingKey)
            calls.size.toLong()
        }
        val streams =
            listOf(
                OutboxStreamKey("local-domain", "order-1"),
                OutboxStreamKey("local-domain", "order-2"),
            )

        assertEquals(listOf(1L, 2L), allocator.nextSequences(streams))
        assertEquals(streams, calls)
    }
}
