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

import com.jstore.payment.domain.payment.PaymentOrderStatus
import com.jstore.payment.domain.payment.PaymentRefundStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "payment_orders")
class PaymentOrderPO(
    @Id var id: Long = 0,
    @Column(name = "order_id", nullable = false, unique = true) var orderId: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "payable_amount", nullable = false, precision = 19, scale = 0)
    var payableAmount: BigDecimal = BigDecimal.ZERO,
    @Column(nullable = false, length = 3) var currency: String = "CNY",
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PaymentOrderStatus = PaymentOrderStatus.PENDING,
    @Column(name = "provider_transaction_id", length = 128)
    var providerTransactionId: String? = null,
    @Column(name = "captured_amount", precision = 19, scale = 0)
    var capturedAmount: BigDecimal? = null,
    @Column(name = "captured_at") var capturedAt: Instant? = null,
    @Version var version: Long = 0,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_order_id")
    var refunds: MutableList<PaymentRefundPO> = mutableListOf(),
)

@Entity
@Table(name = "payment_refunds")
class PaymentRefundPO(
    @Id var id: Long = 0,
    @Column(name = "payment_order_id", insertable = false, updatable = false)
    var paymentOrderId: Long = 0,
    @Column(name = "after_sale_id", nullable = false, unique = true) var afterSaleId: Long = 0,
    @Column(nullable = false, precision = 19, scale = 0) var amount: BigDecimal = BigDecimal.ZERO,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: PaymentRefundStatus = PaymentRefundStatus.PENDING,
    @Column(name = "provider_refund_id", length = 128) var providerRefundId: String? = null,
    @Column(name = "failure_reason", length = 500) var failureReason: String? = null,
    @Column(name = "requested_at", nullable = false) var requestedAt: Instant = Instant.EPOCH,
    @Column(name = "completed_at") var completedAt: Instant? = null,
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_refund_id")
    var items: MutableList<PaymentRefundItemPO> = mutableListOf(),
)

@Entity
@Table(name = "payment_refund_items")
class PaymentRefundItemPO(
    @Id @Column(length = 128) var id: String = "",
    @Column(name = "payment_refund_id", insertable = false, updatable = false)
    var paymentRefundId: Long = 0,
    @Column(name = "order_item_id", nullable = false) var orderItemId: Long = 0,
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
    @Column(nullable = false, precision = 19, scale = 0) var amount: BigDecimal = BigDecimal.ZERO,
)
