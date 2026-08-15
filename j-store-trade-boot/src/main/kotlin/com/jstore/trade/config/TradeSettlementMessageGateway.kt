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

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.CancelPaymentInstallmentCommand
import com.jstore.contracts.commerce.ContractPaymentAllocation
import com.jstore.contracts.commerce.PreparePaymentInstallmentCommand
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.SettlementMode
import com.jstore.trade.domain.SettlementPlanId
import com.jstore.trade.domain.Trade
import com.jstore.trade.domain.TradeErrors
import com.jstore.trade.service.TradeSettlementGateway
import java.time.Duration
import java.time.Instant

class TradeSettlementMessageGateway(
    private val publisher: IntegrationMessagePublisher,
    private val now: () -> Instant = Instant::now,
    private val preparationTimeout: Duration = Duration.ofMinutes(1),
    private val paymentActionLifetime: Duration = Duration.ofMinutes(15),
    private val safetyMargin: Duration = Duration.ofMinutes(2),
) : TradeSettlementGateway {
    init {
        require(!preparationTimeout.isNegative && !preparationTimeout.isZero)
        require(!paymentActionLifetime.isNegative && !paymentActionLifetime.isZero)
        require(!safetyMargin.isNegative)
        require(preparationTimeout < paymentActionLifetime)
    }

    override fun prepareSettlement(
        trade: Trade,
        settlementPlanId: SettlementPlanId,
    ): Result<Unit, BusinessError> {
        if (trade.settlementTerms.mode != SettlementMode.PREPAID) return Success(Unit)
        val installment = trade.settlementTerms.installments.single()
        val occurredAt = now()
        val reservationDeadline = trade.orderPlans.mapNotNull { it.reservationExpiresAt }.min()
        val acceptBefore = occurredAt.plus(preparationTimeout)
        val expiresAt = occurredAt.plus(paymentActionLifetime)
        if (expiresAt.plus(safetyMargin) > reservationDeadline) {
            return com.jstore.common.utils.Failure(TradeErrors.RESERVATION_WINDOW_INSUFFICIENT)
        }
        publisher.publish(
            PreparePaymentInstallmentCommand(
                trade.id.value,
                settlementPlanId.value,
                installment.installmentId,
                installment.amount.fen,
                trade.currency,
                trade.orderPlans.map {
                    ContractPaymentAllocation(
                        it.id.value,
                        requireNotNull(it.orderId),
                        it.merchantId,
                        it.payableAmount.fen,
                    )
                },
                "trade:${trade.id.value}:settlement:${settlementPlanId.value}",
                occurredAt,
                acceptBefore,
                expiresAt,
            )
        )
        return Success(Unit)
    }

    override fun cancelSettlement(
        trade: Trade,
        settlementPlanId: SettlementPlanId,
        reason: String,
    ): Result<Unit, BusinessError> {
        val occurredAt = now()
        trade.settlementTerms.installments.forEach { installment ->
            publisher.publish(
                CancelPaymentInstallmentCommand(
                    trade.id.value,
                    settlementPlanId.value,
                    installment.installmentId,
                    reason,
                    "trade:${trade.id.value}:cancel-settlement:${settlementPlanId.value}",
                    occurredAt,
                )
            )
        }
        return Success(Unit)
    }
}
