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
package com.jstore.payment.config

import com.jstore.common.errors.BusinessError
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.payment.domain.payment.*
import com.jstore.trade.domain.SettlementMode
import com.jstore.trade.domain.SettlementPlanId
import com.jstore.trade.domain.Trade
import com.jstore.trade.service.TradeSettlementGateway

/** Creates the first executable PREPAID/FULL payment from Trade's immutable settlement plan. */
class TradeSettlementAdapter(
    private val payments: TradePaymentRepository,
    private val sequence: SnowFlakSequence,
) : TradeSettlementGateway {
    override fun prepareSettlement(
        trade: Trade,
        settlementPlanId: SettlementPlanId,
    ): Result<Unit, BusinessError> {
        if (trade.settlementTerms.mode != SettlementMode.PREPAID) return Success(Unit)
        val installment = trade.settlementTerms.installments.single()
        val allocations =
            trade.orderPlans.map {
                PaymentAllocationSnapshot(
                    it.id.value,
                    requireNotNull(it.orderId),
                    it.merchantId,
                    it.payableAmount,
                )
            }
        payments.findByInstallment(settlementPlanId.value, installment.installmentId)?.let {
            existing ->
            return if (
                existing.matches(
                    trade.id.value,
                    settlementPlanId.value,
                    installment.installmentId,
                    installment.amount,
                    trade.currency,
                    allocations,
                )
            )
                Success(Unit)
            else Failure(PaymentErrors.ORDER_CONFLICT)
        }
        payments.save(
            TradePayment.prepare(
                TradePaymentId(sequence.nextId()),
                trade.id.value,
                settlementPlanId.value,
                installment.installmentId,
                installment.amount,
                trade.currency,
                allocations,
            )
        )
        return Success(Unit)
    }

    override fun cancelSettlement(
        trade: Trade,
        settlementPlanId: SettlementPlanId,
        reason: String,
    ): Result<Unit, BusinessError> {
        trade.settlementTerms.installments.forEach { installment ->
            val payment =
                payments.findByInstallment(settlementPlanId.value, installment.installmentId)
                    ?: return@forEach
            val changed = payment.cancel(reason)
            changed.onFailure {
                return Failure(it)
            }
            if (changed is Success && changed.value) payments.save(payment)
        }
        return Success(Unit)
    }
}
