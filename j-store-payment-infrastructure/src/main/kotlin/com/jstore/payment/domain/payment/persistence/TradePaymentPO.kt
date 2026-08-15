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
package com.jstore.payment.domain.payment.persistence

import com.jstore.payment.domain.payment.TradePaymentStatus
import jakarta.persistence.*
import java.time.Instant

@Embeddable
class PaymentAllocationPO(
    @Column(name = "order_plan_id", nullable = false) var orderPlanId: Long = 0,
    @Column(name = "order_id", nullable = false) var orderId: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "amount_fen", nullable = false) var amountFen: Long = 0,
)

@Entity
@Table(
    name = "trade_payments",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_trade_payment_installment",
                columnNames = ["settlement_plan_id", "installment_id"],
            )
        ],
)
class TradePaymentPO(
    @Id var id: Long = 0,
    @Column(name = "trade_id", nullable = false) var tradeId: Long = 0,
    @Column(name = "settlement_plan_id", nullable = false) var settlementPlanId: Long = 0,
    @Column(name = "installment_id", nullable = false, length = 128) var installmentId: String = "",
    @Column(name = "payable_amount_fen", nullable = false) var payableAmountFen: Long = 0,
    @Column(nullable = false, length = 3) var currency: String = "CNY",
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "trade_payment_allocations",
        joinColumns = [JoinColumn(name = "payment_id")],
    )
    @OrderColumn(name = "allocation_no")
    var allocations: MutableList<PaymentAllocationPO> = mutableListOf(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: TradePaymentStatus = TradePaymentStatus.PREPARING,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.EPOCH,
    @Version var persistenceVersion: Long = 0,
)
