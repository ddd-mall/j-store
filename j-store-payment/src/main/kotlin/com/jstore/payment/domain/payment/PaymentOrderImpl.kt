package com.jstore.payment.domain.payment

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.event.PaymentCapturedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundFailedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundRequestedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundSucceededEvent
import java.time.Instant
import java.util.LinkedList
import java.util.Queue

class PaymentOrderImpl(
    override val id: PaymentOrderId,
    override val orderId: Long,
    override val merchantId: Long,
    override val payableAmount: Price,
    override val currency: String,
    private var _status: PaymentOrderStatus = PaymentOrderStatus.PENDING,
    private var _capture: PaymentCapture? = null,
    private val _refunds: MutableList<PaymentRefund> = mutableListOf(),
) : PaymentOrder {
    override val domainEventQueue: Queue<DomainEvent> = LinkedList()
    override val status: PaymentOrderStatus
        get() = _status

    override val capture: PaymentCapture?
        get() = _capture

    override val refunds: List<PaymentRefund>
        get() = _refunds.toList()

    init {
        require(orderId > 0 && merchantId > 0 && payableAmount > Price.ZERO)
        require(currency.matches(Regex("[A-Z]{3}")))
    }

    override fun capture(
        providerTransactionId: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        _capture?.let {
            return if (it.providerTransactionId == providerTransactionId && it.amount == amount) {
                Success(false)
            } else {
                Failure(PaymentErrors.CAPTURE_CONFLICT)
            }
        }
        if (_status != PaymentOrderStatus.PENDING) return Failure(PaymentErrors.INVALID_STATE)
        if (
            providerTransactionId.isBlank() || currency != this.currency || amount != payableAmount
        ) {
            return Failure(PaymentErrors.CAPTURE_INVALID)
        }

        _capture = PaymentCapture(providerTransactionId, amount, occurredAt)
        _status = PaymentOrderStatus.CAPTURED
        publishEvent(
            PaymentCapturedEvent(
                id,
                orderId,
                merchantId,
                providerTransactionId,
                amount,
                currency,
                occurredAt,
            )
        )
        return Success(true)
    }

    override fun requestRefund(
        refund: PaymentRefund,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        _refunds
            .firstOrNull { it.afterSaleId == refund.afterSaleId }
            ?.let {
                return Success(false)
            }
        if (_status !in setOf(PaymentOrderStatus.CAPTURED, PaymentOrderStatus.PARTIALLY_REFUNDED)) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        val committedOrPending =
            Price.sumOf(
                _refunds.filter { it.status != PaymentRefundStatus.FAILED }.map { it.amount }
            )
        if (committedOrPending + refund.amount > payableAmount)
            return Failure(PaymentErrors.REFUND_INVALID)

        _refunds += refund
        publishRefundRequested(refund, occurredAt)
        return Success(true)
    }

    override fun retryRefund(
        refundId: PaymentRefundId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val refund =
            _refunds.firstOrNull { it.id == refundId }
                ?: return Failure(PaymentErrors.REFUND_NOT_FOUND)
        if (refund.status == PaymentRefundStatus.PENDING) return Success(false)
        if (refund.status != PaymentRefundStatus.FAILED) return Failure(PaymentErrors.INVALID_STATE)
        refund.status = PaymentRefundStatus.PENDING
        refund.failureReason = null
        refund.completedAt = null
        publishRefundRequested(refund, occurredAt)
        return Success(true)
    }

    override fun markRefundSucceeded(
        refundId: PaymentRefundId,
        providerRefundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val refund =
            _refunds.firstOrNull { it.id == refundId }
                ?: return Failure(PaymentErrors.REFUND_NOT_FOUND)
        if (refund.status == PaymentRefundStatus.SUCCEEDED) {
            return if (refund.providerRefundId == providerRefundId) Success(false)
            else Failure(PaymentErrors.REFUND_PROVIDER_CONFLICT)
        }
        if (refund.status != PaymentRefundStatus.PENDING || providerRefundId.isBlank()) {
            return Failure(PaymentErrors.INVALID_STATE)
        }
        refund.status = PaymentRefundStatus.SUCCEEDED
        refund.providerRefundId = providerRefundId
        refund.completedAt = occurredAt
        val refunded =
            Price.sumOf(
                _refunds.filter { it.status == PaymentRefundStatus.SUCCEEDED }.map { it.amount }
            )
        _status =
            if (refunded == payableAmount) PaymentOrderStatus.REFUNDED
            else PaymentOrderStatus.PARTIALLY_REFUNDED
        publishEvent(
            PaymentRefundSucceededEvent(
                id,
                refund.id,
                orderId,
                refund.afterSaleId,
                merchantId,
                providerRefundId,
                refund.items,
                refund.amount,
                currency,
                occurredAt,
            )
        )
        return Success(true)
    }

    override fun markRefundFailed(
        refundId: PaymentRefundId,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val refund =
            _refunds.firstOrNull { it.id == refundId }
                ?: return Failure(PaymentErrors.REFUND_NOT_FOUND)
        if (refund.status == PaymentRefundStatus.FAILED && refund.failureReason == reason)
            return Success(false)
        if (refund.status != PaymentRefundStatus.PENDING || reason.isBlank())
            return Failure(PaymentErrors.INVALID_STATE)
        refund.status = PaymentRefundStatus.FAILED
        refund.failureReason = reason
        refund.completedAt = occurredAt
        publishEvent(
            PaymentRefundFailedEvent(id, refund.id, orderId, refund.afterSaleId, reason, occurredAt)
        )
        return Success(true)
    }

    private fun publishRefundRequested(refund: PaymentRefund, occurredAt: Instant) {
        publishEvent(
            PaymentRefundRequestedEvent(
                id,
                refund.id,
                orderId,
                refund.afterSaleId,
                refund.amount,
                currency,
                occurredAt,
            )
        )
    }
}
