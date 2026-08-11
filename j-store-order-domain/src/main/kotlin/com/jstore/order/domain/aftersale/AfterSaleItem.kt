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
package com.jstore.order.domain.aftersale

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId

interface AfterSaleItem : Entity<AfterSaleItemId> {
    val orderId: OrderId
    val orderItemId: OrderItemId
    val requestedQuantity: Int
    val requestedAmount: Price
    val currency: String
    val eligibilitySnapshot: RefundEligibilitySnapshot
}

data class AfterSaleItemImpl(
    override val id: AfterSaleItemId,
    override val orderId: OrderId,
    override val orderItemId: OrderItemId,
    override val requestedQuantity: Int,
    override val requestedAmount: Price,
    override val currency: String,
    override val eligibilitySnapshot: RefundEligibilitySnapshot,
) : AfterSaleItem {
    init {
        require(
            requestedQuantity > 0 && requestedQuantity <= eligibilitySnapshot.refundableQuantity
        )
        require(
            requestedAmount > Price.ZERO && requestedAmount <= eligibilitySnapshot.refundableAmount
        )
        require(currency == eligibilitySnapshot.currency)
    }
}
