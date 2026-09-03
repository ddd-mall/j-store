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

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRepository
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId

class RefundCapacity(
    override val id: OrderItemId,
    val orderId: OrderId,
    val quantityCeiling: Int,
    val amountCeiling: Price,
    requestedQuantity: Int = 0,
    requestedAmount: Price = Price.ZERO,
    approvedQuantity: Int = 0,
    approvedAmount: Price = Price.ZERO,
    val persistenceVersion: Long = 0,
) : AggregateRoot<OrderItemId> {
    private var mutableRequestedQuantity = requestedQuantity
    private var mutableRequestedAmount = requestedAmount
    private var mutableApprovedQuantity = approvedQuantity
    private var mutableApprovedAmount = approvedAmount

    val requestedQuantity: Int
        get() = mutableRequestedQuantity

    val requestedAmount: Price
        get() = mutableRequestedAmount

    val approvedQuantity: Int
        get() = mutableApprovedQuantity

    val approvedAmount: Price
        get() = mutableApprovedAmount

    fun matches(ceiling: RefundCapacityCeiling): Boolean =
        orderId == ceiling.orderId &&
            id == ceiling.orderItemId &&
            quantityCeiling == ceiling.quantity &&
            amountCeiling == ceiling.amount

    fun reserve(quantity: Int, amount: Price): Result<Unit, BusinessError> {
        if (
            mutableRequestedQuantity + mutableApprovedQuantity + quantity > quantityCeiling ||
                mutableRequestedAmount + mutableApprovedAmount + amount > amountCeiling
        )
            return Failure(AfterSaleErrors.CAPACITY_EXCEEDED)
        mutableRequestedQuantity += quantity
        mutableRequestedAmount += amount
        return Success(Unit)
    }

    fun settle(
        quantity: Int,
        amount: Price,
        action: AllocationAction,
    ): Result<Unit, BusinessError> {
        if (mutableRequestedQuantity < quantity || mutableRequestedAmount < amount)
            return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
        mutableRequestedQuantity -= quantity
        mutableRequestedAmount -= amount
        if (action == AllocationAction.APPROVE) {
            mutableApprovedQuantity += quantity
            mutableApprovedAmount += amount
        }
        return Success(Unit)
    }

    companion object {
        fun from(ceiling: RefundCapacityCeiling) =
            RefundCapacity(ceiling.orderItemId, ceiling.orderId, ceiling.quantity, ceiling.amount)
    }
}

interface RefundCapacityRepository : AggregateRepository<OrderItemId, RefundCapacity> {
    fun initializeIfAbsent(capacities: List<RefundCapacity>)

    fun lockAll(ids: Collection<OrderItemId>): List<RefundCapacity>
}

interface AfterSaleCommandReceiptStore {
    fun find(actorId: Long, type: AfterSaleCommandType, key: String): AfterSaleCommandReceipt?

    fun claim(receipt: AfterSaleCommandReceipt): Boolean
}
