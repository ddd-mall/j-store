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
package com.jstore.common.framework

import com.jstore.common.properties.Id
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IdentifierTest {
    @Test
    fun `non data identifier subclasses use value equality`() {
        assertEquals(TestId(42), TestId(42))
        assertEquals(TestId(42).hashCode(), TestId(42).hashCode())
    }

    @Test
    fun `different identifier types are never equal`() {
        assertNotEquals<Any>(TestId(42), OtherId(42))
    }

    private class TestId(override val value: Long) : Id<Long>(value)

    private class OtherId(override val value: Long) : Id<Long>(value)
}
