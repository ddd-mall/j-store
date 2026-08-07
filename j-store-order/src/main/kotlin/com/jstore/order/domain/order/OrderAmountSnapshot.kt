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
package com.jstore.order.domain.order

import com.jstore.common.properties.Price

/** 下单时冻结的金额组成。后续商品价格变化不得影响该快照。 */
data class OrderAmountSnapshot(
    val currency: String,
    val itemsSubtotal: Price,
    val discountAmount: Price,
    val shippingAmount: Price,
    val taxAmount: Price,
    val payableAmount: Price,
) {
    init {
        require(currency.matches(Regex("[A-Z]{3}"))) { "currency must be an ISO-4217 code" }
        require(discountAmount <= itemsSubtotal) { "discount cannot exceed item subtotal" }
        require(payableAmount == itemsSubtotal - discountAmount + shippingAmount + taxAmount) {
            "payable amount does not match amount components"
        }
    }

    companion object {
        fun cny(itemsSubtotal: Price): OrderAmountSnapshot =
            OrderAmountSnapshot(
                currency = "CNY",
                itemsSubtotal = itemsSubtotal,
                discountAmount = Price.ZERO,
                shippingAmount = Price.ZERO,
                taxAmount = Price.ZERO,
                payableAmount = itemsSubtotal,
            )
    }
}
