package com.jstore.order.domain.aftersale

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.utils.Result
import com.jstore.order.domain.order.OrderId
import java.time.Instant
import java.time.LocalDateTime

interface AfterSale : AgreeGate<AfterSaleId> {
    override val id: AfterSaleId
    val orderId: OrderId
    val applicantId: ApplicantActorId
    val merchantId: MerchantActorId
    val status: AfterSaleStatus
    val reason: RefundReason
    val fulfillmentSnapshot: FulfillmentSnapshot
    val items: List<AfterSaleItem>
    val reviewDecision: ReviewDecision?
    val cancelledAt: LocalDateTime?
    val returnReceivedAt: LocalDateTime?
    val refundId: String?
    val refundFailureReason: String?
    val createTime: LocalDateTime
    val updateTime: LocalDateTime
    val version: Long

    fun approve(reviewerId: MerchantActorId, occurredAt: Instant): Result<Unit, BusinessError>

    fun reject(
        reviewerId: MerchantActorId,
        reason: String,
        occurredAt: Instant,
    ): Result<Unit, BusinessError>

    fun cancel(applicantId: ApplicantActorId, occurredAt: Instant): Result<Unit, BusinessError>

    fun receiveReturn(
        reviewerId: MerchantActorId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun retryRefund(
        reviewerId: MerchantActorId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun markRefundSucceeded(refundId: String, occurredAt: Instant): Result<Boolean, BusinessError>

    fun markRefundFailed(
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>
}
