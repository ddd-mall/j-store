package com.jstore.order.domain.aftersale

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.event.*
import com.jstore.order.domain.order.OrderId
import java.time.*
import java.util.LinkedList
import java.util.Queue

class AfterSaleImpl(override val id: AfterSaleId, override val orderId: OrderId, override val applicantId: ApplicantActorId, override val merchantId: MerchantActorId, private var _status: AfterSaleStatus, override val reason: RefundReason, override val fulfillmentSnapshot: FulfillmentSnapshot, items: List<AfterSaleItem>, private var _reviewDecision: ReviewDecision? = null, private var _cancelledAt: LocalDateTime? = null, override val createTime: LocalDateTime, private var _updateTime: LocalDateTime, override val version: Long = 0, override val domainEventQueue: Queue<DomainEvent> = LinkedList()) : AfterSale {
    private val _items = items.toList()
    override val items get() = _items.toList(); override val status get() = _status
    override val reviewDecision get() = _reviewDecision; override val cancelledAt get() = _cancelledAt; override val updateTime get() = _updateTime
    init { require(_items.isNotEmpty()); require(_items.all { it.orderId == orderId }); require(_items.map { it.orderItemId }.toSet().size == _items.size); validateState() }
    override fun approve(reviewerId: MerchantActorId, occurredAt: Instant): Result<Unit, BusinessError> {
        if (reviewerId != merchantId) return Failure(AfterSaleErrors.MERCHANT_FORBIDDEN); if (_status != AfterSaleStatus.REQUESTED) return Failure(AfterSaleErrors.ILLEGAL_STATE)
        val at = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC); _reviewDecision = ReviewDecision(reviewerId, at, null); _status = AfterSaleStatus.APPROVED; _updateTime = at
        publishEvent(AfterSaleApprovedEvent(id, orderId, reviewerId, eventItems(), fulfillmentSnapshot.requireReturn, occurredAt)); return Success(Unit)
    }
    override fun reject(reviewerId: MerchantActorId, reason: String, occurredAt: Instant): Result<Unit, BusinessError> {
        if (reviewerId != merchantId) return Failure(AfterSaleErrors.MERCHANT_FORBIDDEN); if (_status != AfterSaleStatus.REQUESTED) return Failure(AfterSaleErrors.ILLEGAL_STATE)
        val normalized = reason.trim(); if (normalized.length !in 1..500) return Failure(AfterSaleErrors.REJECTION_REASON_INVALID)
        val at = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC); _reviewDecision = ReviewDecision(reviewerId, at, normalized); _status = AfterSaleStatus.REJECTED; _updateTime = at
        publishEvent(AfterSaleRejectedEvent(id, orderId, reviewerId, normalized, occurredAt)); return Success(Unit)
    }
    override fun cancel(applicantId: ApplicantActorId, occurredAt: Instant): Result<Unit, BusinessError> {
        if (applicantId != this.applicantId) return Failure(AfterSaleErrors.APPLICANT_FORBIDDEN); if (_status != AfterSaleStatus.REQUESTED) return Failure(AfterSaleErrors.ILLEGAL_STATE)
        val at = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC); _cancelledAt = at; _status = AfterSaleStatus.CANCELLED; _updateTime = at
        publishEvent(AfterSaleCancelledEvent(id, orderId, applicantId, occurredAt)); return Success(Unit)
    }
    private fun eventItems() = _items.map { AfterSaleEventItem(it.orderItemId, it.eligibilitySnapshot.goods.skuId, it.requestedQuantity, it.requestedAmount, it.currency) }
    private fun validateState() { require(when (_status) { AfterSaleStatus.REQUESTED -> _reviewDecision == null && _cancelledAt == null; AfterSaleStatus.APPROVED -> _reviewDecision?.rejectionReason == null && _cancelledAt == null; AfterSaleStatus.REJECTED -> !_reviewDecision?.rejectionReason.isNullOrBlank() && _cancelledAt == null; AfterSaleStatus.CANCELLED -> _reviewDecision == null && _cancelledAt != null }) }
}
