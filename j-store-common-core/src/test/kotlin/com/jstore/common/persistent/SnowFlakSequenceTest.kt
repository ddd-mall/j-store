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
package com.jstore.common.persistent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SnowFlakSequenceTest {
    @Test
    fun `current epoch ids remain positive and preserve worker bit layout`() {
        val workerOne = SnowFlakSequence(workerId = 1, datacenterId = 2)
        val workerTwo = SnowFlakSequence(workerId = 2, datacenterId = 2)

        val first = workerOne.nextId()
        val second = workerTwo.nextId()

        assertTrue(first > 0)
        assertTrue(second > 0)
        assertEquals(1, ((first shr 12) and 0x1f).toInt())
        assertEquals(2, ((second shr 12) and 0x1f).toInt())
        assertEquals(2, ((first shr 17) and 0x1f).toInt())
    }
}
