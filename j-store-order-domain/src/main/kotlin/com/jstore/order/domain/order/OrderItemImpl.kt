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

/** 订单行项实体实现 */
class OrderItemImpl(
    override val id: OrderItemId,
    override val skuId: Long,
    override val spuId: Long,
    override val goodsName: String,
    override val skuDescription: String,
    override val quantity: Int,
    override val unitPrice: Price,
    override val snapshotVersion: Long = 0,
    override var status: OrderItemStatus = OrderItemStatus.NONE,
    private var _refundedQuantity: Int = 0,
    private var _refundedAmount: Price = Price.ZERO,
) : OrderItem {
    override val purchasedAmount
        get() = subtotal()

    override val refundedQuantity
        get() = _refundedQuantity

    override val refundedAmount
        get() = _refundedAmount

    override val refundableQuantity
        get() = quantity - _refundedQuantity

    override val refundableAmount
        get() = purchasedAmount - _refundedAmount

    init {
        require(quantity > 0 && _refundedQuantity in 0..quantity && _refundedAmount <= subtotal())
    }

    override fun subtotal(): Price = unitPrice * quantity

    fun markCanceled() {
        status = OrderItemStatus.CANCELED
    }

    internal fun markWaitingShipment() {
        status = OrderItemStatus.WAIT_SHIPPING
    }

    internal fun markShipping() {
        status = OrderItemStatus.SHIPPING
    }

    internal fun markDelivered() {
        status = OrderItemStatus.SHIPPING_FINISHED
    }

    internal fun registerRefund(quantity: Int, amount: Price) {
        require(
            quantity > 0 &&
                quantity <= refundableQuantity &&
                amount > Price.ZERO &&
                amount <= refundableAmount
        )
        _refundedQuantity += quantity
        _refundedAmount += amount
    }
}
