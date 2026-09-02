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
package com.jstore.common.currency

import io.kotest.assertions.throwables.shouldThrow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiteCurrencyPolicyTest {
    @Test
    fun `uses configured default and accepts only configured legal currencies`() {
        val policy = SiteCurrencyPolicy("JPY", setOf("JPY", "USD"))

        assertEquals("JPY", policy.select(null))
        assertEquals("USD", policy.select("USD"))
        assertNull(policy.select("CNY"))
        assertNull(policy.select("ZZZ"))
    }

    @Test
    fun `configuration requires legal currencies and an allowed default`() {
        shouldThrow<IllegalArgumentException> { SiteCurrencyPolicy("ZZZ", setOf("ZZZ")) }
        shouldThrow<IllegalArgumentException> { SiteCurrencyPolicy("JPY", setOf("USD")) }
    }
}
