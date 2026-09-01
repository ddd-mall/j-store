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
package com.jstore.trade.config

import com.jstore.cart.api.*
import com.jstore.common.utils.Success
import com.jstore.trade.service.CheckoutRecipient
import com.jstore.trade.service.CreateCheckoutCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CartCheckoutSourceAdapterTest {
    @Test
    fun `one cart checkout resolves three items from two merchants into trusted trade input`() {
        val source =
            CartCheckoutSource(
                9,
                4,
                "digest",
                "CN",
                "ONLINE",
                "CNY",
                listOf(
                    CartCheckoutLine(1, 11, 2, 101, 1001, 1, 3),
                    CartCheckoutLine(2, 12, 2, 102, 1002, 2, 3),
                    CartCheckoutLine(3, 21, 5, 201, 2001, 1, 8),
                ),
            )
        val adapter = CartCheckoutSourceAdapter { CartCheckoutSourceResult.Found(source) }
        val command =
            CreateCheckoutCommand(
                "request",
                42,
                CheckoutRecipient("张三", "CN", "13800000000", null, "110105", "示例路"),
                cartId = 9,
                expectedCartVersion = 4,
            )

        val resolved = assertIs<Success<CreateCheckoutCommand>>(adapter.resolve(command)).value

        assertEquals(3, resolved.items.size)
        assertEquals("digest", resolved.cartDigest)
        assertEquals(setOf(11L, 12L, 21L), resolved.items.map { it.offerId }.toSet())
    }
}
