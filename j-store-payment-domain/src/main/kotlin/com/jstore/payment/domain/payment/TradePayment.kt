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
package com.jstore.payment.domain.payment

import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Instant

data class TradePaymentId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class PaymentAllocationSnapshot(
    val orderPlanId: Long,
    val orderId: Long,
    val merchantId: Long,
    val amount: Price,
) {
    init {
        require(orderPlanId > 0 && orderId > 0 && merchantId > 0 && amount > Price.ZERO)
    }
}

enum class TradePaymentStatus {
    PREPARING,
    READY,
    CAPTURED,
    UNCERTAIN,
    CANCELLED,
    REFUNDED,
}

class TradePayment(
    override val id: TradePaymentId,
    val tradeId: Long,
    val settlementPlanId: Long,
    val installmentId: String,
    val payableAmount: Price,
    val currency: String,
    allocations: List<PaymentAllocationSnapshot>,
    initialStatus: TradePaymentStatus,
    val createdAt: Instant,
    val persistenceVersion: Long = 0,
) : AggregateRoot<TradePaymentId> {
    val allocations: List<PaymentAllocationSnapshot> = allocations.toList()
    private var _status: TradePaymentStatus = initialStatus
    val status: TradePaymentStatus
        get() = _status

    init {
        require(tradeId > 0 && settlementPlanId > 0 && installmentId.isNotBlank())
        require(currency.matches(Regex("[A-Z]{3}")) && this.allocations.isNotEmpty())
        require(this.allocations.map { it.orderPlanId }.distinct().size == this.allocations.size)
        require(Price.sumOf(this.allocations.map { it.amount }) == payableAmount)
    }

    fun matches(
        tradeId: Long,
        settlementPlanId: Long,
        installmentId: String,
        amount: Price,
        currency: String,
        allocations: List<PaymentAllocationSnapshot>,
    ) =
        this.tradeId == tradeId &&
            this.settlementPlanId == settlementPlanId &&
            this.installmentId == installmentId &&
            payableAmount == amount &&
            this.currency == currency &&
            this.allocations == allocations

    fun cancel(reason: String): Result<Boolean, com.jstore.common.errors.BusinessError> {
        if (reason.isBlank()) return Failure(PaymentErrors.INVALID_STATE)
        if (status == TradePaymentStatus.CANCELLED) return Success(false)
        if (status !in setOf(TradePaymentStatus.PREPARING, TradePaymentStatus.READY)) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        _status = TradePaymentStatus.CANCELLED
        return Success(true)
    }

    companion object {
        fun prepare(
            id: TradePaymentId,
            tradeId: Long,
            settlementPlanId: Long,
            installmentId: String,
            payableAmount: Price,
            currency: String,
            allocations: List<PaymentAllocationSnapshot>,
            createdAt: Instant = Instant.now(),
        ) =
            TradePayment(
                id,
                tradeId,
                settlementPlanId,
                installmentId,
                payableAmount,
                currency,
                allocations,
                TradePaymentStatus.PREPARING,
                createdAt,
            )
    }
}
