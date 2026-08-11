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

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.framework.RecordsDomainEvents
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import java.time.Instant

data class PaymentOrderId(override val value: Long) : Id<Long>(value)

data class PaymentRefundId(override val value: Long) : Id<Long>(value)

enum class PaymentOrderStatus {
    PENDING,
    CAPTURED,
    PARTIALLY_REFUNDED,
    REFUNDED,
}

enum class PaymentRefundStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
}

data class PaymentCapture(
    val providerTransactionId: String,
    val amount: Price,
    val capturedAt: Instant,
)

data class PaymentRefundItem(
    val orderItemId: Long,
    val skuId: Long,
    val quantity: Int,
    val amount: Price,
) {
    init {
        require(orderItemId > 0 && skuId > 0 && quantity > 0 && amount > Price.ZERO)
    }
}

class PaymentRefund(
    val id: PaymentRefundId,
    val afterSaleId: Long,
    val items: List<PaymentRefundItem>,
    val amount: Price,
    status: PaymentRefundStatus = PaymentRefundStatus.PENDING,
    providerRefundId: String? = null,
    failureReason: String? = null,
    val requestedAt: Instant,
    completedAt: Instant? = null,
) {
    private var _status: PaymentRefundStatus = status
    private var _providerRefundId: String? = providerRefundId
    private var _failureReason: String? = failureReason
    private var _completedAt: Instant? = completedAt

    val status: PaymentRefundStatus
        get() = _status

    val providerRefundId: String?
        get() = _providerRefundId

    val failureReason: String?
        get() = _failureReason

    val completedAt: Instant?
        get() = _completedAt

    init {
        require(afterSaleId > 0 && items.isNotEmpty())
        require(amount == Price.sumOf(items.map { it.amount }))
    }

    internal fun markPending() {
        _status = PaymentRefundStatus.PENDING
        _failureReason = null
        _completedAt = null
    }

    internal fun markSucceeded(providerRefundId: String, completedAt: Instant) {
        _status = PaymentRefundStatus.SUCCEEDED
        _providerRefundId = providerRefundId
        _failureReason = null
        _completedAt = completedAt
    }

    internal fun markFailed(reason: String, completedAt: Instant) {
        _status = PaymentRefundStatus.FAILED
        _failureReason = reason
        _completedAt = completedAt
    }
}

interface PaymentOrder : AggregateRoot<PaymentOrderId>, RecordsDomainEvents {
    val orderId: Long
    val merchantId: Long
    val payableAmount: Price
    val currency: String
    val status: PaymentOrderStatus
    val capture: PaymentCapture?
    val refunds: List<PaymentRefund>

    fun capture(
        providerTransactionId: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun requestRefund(refund: PaymentRefund, occurredAt: Instant): Result<Boolean, BusinessError>

    fun retryRefund(refundId: PaymentRefundId, occurredAt: Instant): Result<Boolean, BusinessError>

    fun markRefundSucceeded(
        refundId: PaymentRefundId,
        providerRefundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun markRefundFailed(
        refundId: PaymentRefundId,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>
}
