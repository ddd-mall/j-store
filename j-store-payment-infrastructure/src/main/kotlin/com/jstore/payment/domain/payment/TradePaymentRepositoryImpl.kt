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

import com.jstore.common.properties.Price
import com.jstore.payment.domain.payment.persistence.PaymentAllocationPO
import com.jstore.payment.domain.payment.persistence.TradePaymentPO
import com.jstore.payment.domain.payment.persistence.TradePaymentPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class TradePaymentRepositoryImpl(private val jpa: TradePaymentPOJpaRepository) :
    TradePaymentRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: TradePayment): TradePayment = toDomain(jpa.save(toPO(aggregate)))

    override fun findById(id: TradePaymentId): TradePayment? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByInstallment(settlementPlanId: Long, installmentId: String): TradePayment? =
        jpa.findBySettlementPlanIdAndInstallmentId(settlementPlanId, installmentId)?.let(::toDomain)

    private fun toPO(payment: TradePayment) =
        TradePaymentPO(
            id = payment.id.value,
            tradeId = payment.tradeId,
            settlementPlanId = payment.settlementPlanId,
            installmentId = payment.installmentId,
            payableAmountFen = payment.payableAmount.fen,
            currency = payment.currency,
            allocations =
                payment.allocations
                    .map {
                        PaymentAllocationPO(
                            it.orderPlanId,
                            it.orderId,
                            it.merchantId,
                            it.amount.fen,
                        )
                    }
                    .toMutableList(),
            status = payment.status,
            providerReference = payment.providerReference,
            payAction = payment.payAction,
            providerAcceptedAt = payment.providerAcceptedAt,
            acceptBefore = payment.acceptBefore,
            expiresAt = payment.expiresAt,
            failureReason = payment.failureReason,
            cancellationReason = payment.cancellationReason,
            createdAt = payment.createdAt,
            persistenceVersion = payment.persistenceVersion,
        )

    private fun toDomain(po: TradePaymentPO) =
        TradePayment(
            id = TradePaymentId(po.id),
            tradeId = po.tradeId,
            settlementPlanId = po.settlementPlanId,
            installmentId = po.installmentId,
            payableAmount = Price.ofFen(po.payableAmountFen),
            currency = po.currency,
            allocations =
                po.allocations.map {
                    PaymentAllocationSnapshot(
                        it.orderPlanId,
                        it.orderId,
                        it.merchantId,
                        Price.ofFen(it.amountFen),
                    )
                },
            initialStatus = po.status,
            providerReference = po.providerReference,
            payAction = po.payAction,
            providerAcceptedAt = po.providerAcceptedAt,
            acceptBefore = po.acceptBefore,
            expiresAt = po.expiresAt,
            failureReason = po.failureReason,
            cancellationReason = po.cancellationReason,
            createdAt = po.createdAt,
            persistenceVersion = po.persistenceVersion,
        )
}
