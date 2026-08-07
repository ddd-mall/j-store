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
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.event.AfterSaleApprovedEvent
import com.jstore.order.domain.aftersale.event.AfterSaleCancelledEvent
import com.jstore.order.domain.aftersale.event.AfterSaleEventItem
import com.jstore.order.domain.aftersale.event.AfterSaleRefundFailedEvent
import com.jstore.order.domain.aftersale.event.AfterSaleRefundRequestedEvent
import com.jstore.order.domain.aftersale.event.AfterSaleRefundSucceededEvent
import com.jstore.order.domain.aftersale.event.AfterSaleRejectedEvent
import com.jstore.order.domain.aftersale.event.AfterSaleReturnReceivedEvent
import com.jstore.order.domain.order.OrderId
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.LinkedList
import java.util.Queue

class AfterSaleImpl(
    override val id: AfterSaleId,
    override val orderId: OrderId,
    override val applicantId: ApplicantActorId,
    override val merchantId: MerchantActorId,
    private var _status: AfterSaleStatus,
    override val reason: RefundReason,
    override val fulfillmentSnapshot: FulfillmentSnapshot,
    items: List<AfterSaleItem>,
    private var _reviewDecision: ReviewDecision? = null,
    private var _cancelledAt: LocalDateTime? = null,
    private var _returnReceivedAt: LocalDateTime? = null,
    private var _refundId: String? = null,
    private var _refundFailureReason: String? = null,
    override val createTime: LocalDateTime,
    private var _updateTime: LocalDateTime,
    override val version: Long = 0,
    override val domainEventQueue: Queue<DomainEvent> = LinkedList(),
) : AfterSale {
    private val _items = items.toList()
    override val items: List<AfterSaleItem>
        get() = _items.toList()

    override val status: AfterSaleStatus
        get() = _status

    override val reviewDecision: ReviewDecision?
        get() = _reviewDecision

    override val cancelledAt: LocalDateTime?
        get() = _cancelledAt

    override val returnReceivedAt: LocalDateTime?
        get() = _returnReceivedAt

    override val refundId: String?
        get() = _refundId

    override val refundFailureReason: String?
        get() = _refundFailureReason

    override val updateTime: LocalDateTime
        get() = _updateTime

    init {
        require(_items.isNotEmpty())
        require(_items.all { it.orderId == orderId })
        require(_items.map { it.orderItemId }.toSet().size == _items.size)
        validateState()
    }

    override fun approve(
        reviewerId: MerchantActorId,
        occurredAt: Instant,
    ): Result<Unit, BusinessError> {
        if (reviewerId != merchantId) return Failure(AfterSaleErrors.MERCHANT_FORBIDDEN)
        if (_status != AfterSaleStatus.REQUESTED) return Failure(AfterSaleErrors.ILLEGAL_STATE)
        val at = occurredAt.toUtcLocalDateTime()
        _reviewDecision = ReviewDecision(reviewerId, at, null)
        _status =
            if (fulfillmentSnapshot.requireReturn) AfterSaleStatus.RETURN_REQUIRED
            else AfterSaleStatus.REFUND_PENDING
        _updateTime = at
        publishEvent(
            AfterSaleApprovedEvent(
                id,
                orderId,
                reviewerId,
                eventItems(),
                fulfillmentSnapshot.requireReturn,
                occurredAt,
            )
        )
        if (!fulfillmentSnapshot.requireReturn) publishRefundRequested(occurredAt)
        return Success(Unit)
    }

    override fun reject(
        reviewerId: MerchantActorId,
        reason: String,
        occurredAt: Instant,
    ): Result<Unit, BusinessError> {
        if (reviewerId != merchantId) return Failure(AfterSaleErrors.MERCHANT_FORBIDDEN)
        if (_status != AfterSaleStatus.REQUESTED) return Failure(AfterSaleErrors.ILLEGAL_STATE)
        val normalized = reason.trim()
        if (normalized.length !in 1..500) return Failure(AfterSaleErrors.REJECTION_REASON_INVALID)
        val at = occurredAt.toUtcLocalDateTime()
        _reviewDecision = ReviewDecision(reviewerId, at, normalized)
        _status = AfterSaleStatus.REJECTED
        _updateTime = at
        publishEvent(AfterSaleRejectedEvent(id, orderId, reviewerId, normalized, occurredAt))
        return Success(Unit)
    }

    override fun cancel(
        applicantId: ApplicantActorId,
        occurredAt: Instant,
    ): Result<Unit, BusinessError> {
        if (applicantId != this.applicantId) return Failure(AfterSaleErrors.APPLICANT_FORBIDDEN)
        if (_status != AfterSaleStatus.REQUESTED) return Failure(AfterSaleErrors.ILLEGAL_STATE)
        val at = occurredAt.toUtcLocalDateTime()
        _cancelledAt = at
        _status = AfterSaleStatus.CANCELLED
        _updateTime = at
        publishEvent(AfterSaleCancelledEvent(id, orderId, applicantId, occurredAt))
        return Success(Unit)
    }

    override fun receiveReturn(
        reviewerId: MerchantActorId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        if (reviewerId != merchantId) return Failure(AfterSaleErrors.MERCHANT_FORBIDDEN)
        if (_status == AfterSaleStatus.REFUND_PENDING && _returnReceivedAt != null)
            return Success(false)
        if (_status != AfterSaleStatus.RETURN_REQUIRED)
            return Failure(AfterSaleErrors.ILLEGAL_STATE)
        val at = occurredAt.toUtcLocalDateTime()
        _returnReceivedAt = at
        _status = AfterSaleStatus.REFUND_PENDING
        _updateTime = at
        publishEvent(
            AfterSaleReturnReceivedEvent(id, orderId, reviewerId, eventItems(), occurredAt)
        )
        publishRefundRequested(occurredAt)
        return Success(true)
    }

    override fun retryRefund(
        reviewerId: MerchantActorId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        if (reviewerId != merchantId) return Failure(AfterSaleErrors.MERCHANT_FORBIDDEN)
        if (_status == AfterSaleStatus.REFUND_PENDING) return Success(false)
        if (_status != AfterSaleStatus.REFUND_FAILED) return Failure(AfterSaleErrors.ILLEGAL_STATE)
        _status = AfterSaleStatus.REFUND_PENDING
        _refundFailureReason = null
        _updateTime = occurredAt.toUtcLocalDateTime()
        publishRefundRequested(occurredAt)
        return Success(true)
    }

    override fun markRefundSucceeded(
        refundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        if (_status == AfterSaleStatus.COMPLETED) {
            return if (_refundId == refundId) Success(false)
            else Failure(AfterSaleErrors.REFUND_REFERENCE_CONFLICT)
        }
        if (_status != AfterSaleStatus.REFUND_PENDING || refundId.isBlank())
            return Failure(AfterSaleErrors.ILLEGAL_STATE)
        _refundId = refundId
        _refundFailureReason = null
        _status = AfterSaleStatus.COMPLETED
        _updateTime = occurredAt.toUtcLocalDateTime()
        publishEvent(
            AfterSaleRefundSucceededEvent(
                id,
                orderId,
                refundId,
                eventItems(),
                totalAmount(),
                currency(),
                occurredAt,
            )
        )
        return Success(true)
    }

    override fun markRefundFailed(
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val normalizedReason = reason.trim()
        if (
            _status == AfterSaleStatus.REFUND_FAILED &&
                _refundId == refundId &&
                _refundFailureReason == normalizedReason
        ) {
            return Success(false)
        }
        if (
            _status != AfterSaleStatus.REFUND_PENDING ||
                refundId.isBlank() ||
                normalizedReason.isBlank()
        ) {
            return Failure(AfterSaleErrors.ILLEGAL_STATE)
        }
        _refundId = refundId
        _refundFailureReason = normalizedReason
        _status = AfterSaleStatus.REFUND_FAILED
        _updateTime = occurredAt.toUtcLocalDateTime()
        publishEvent(
            AfterSaleRefundFailedEvent(id, orderId, refundId, normalizedReason, occurredAt)
        )
        return Success(true)
    }

    private fun publishRefundRequested(occurredAt: Instant) {
        publishEvent(
            AfterSaleRefundRequestedEvent(
                id,
                orderId,
                merchantId,
                eventItems(),
                totalAmount(),
                currency(),
                occurredAt,
            )
        )
    }

    private fun totalAmount(): Price = Price.sumOf(_items.map { it.requestedAmount })

    private fun currency(): String = _items.first().currency

    private fun eventItems() = _items.map {
        AfterSaleEventItem(
            it.orderItemId,
            it.eligibilitySnapshot.goods.skuId,
            it.requestedQuantity,
            it.requestedAmount,
            it.currency,
        )
    }

    private fun validateState() {
        require(
            when (_status) {
                AfterSaleStatus.REQUESTED -> _reviewDecision == null && _cancelledAt == null
                AfterSaleStatus.RETURN_REQUIRED,
                AfterSaleStatus.REFUND_PENDING,
                AfterSaleStatus.REFUND_FAILED,
                AfterSaleStatus.COMPLETED ->
                    _reviewDecision?.rejectionReason == null && _cancelledAt == null
                AfterSaleStatus.REJECTED ->
                    !_reviewDecision?.rejectionReason.isNullOrBlank() && _cancelledAt == null
                AfterSaleStatus.CANCELLED -> _reviewDecision == null && _cancelledAt != null
            }
        )
    }

    private fun Instant.toUtcLocalDateTime(): LocalDateTime =
        LocalDateTime.ofInstant(this, ZoneOffset.UTC)
}
