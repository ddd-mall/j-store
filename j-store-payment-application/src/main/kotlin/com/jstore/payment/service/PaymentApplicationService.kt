package com.jstore.payment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import com.jstore.common.utils.onFailure
import com.jstore.payment.domain.payment.PaymentErrors
import com.jstore.payment.domain.payment.PaymentOrder
import com.jstore.payment.domain.payment.PaymentOrderId
import com.jstore.payment.domain.payment.PaymentOrderImpl
import com.jstore.payment.domain.payment.PaymentOrderRepository
import com.jstore.payment.domain.payment.PaymentRefund
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.domain.payment.PaymentRefundItem
import java.time.Instant

data class PaymentOrderRequest(
    val orderId: Long,
    val merchantId: Long,
    val payableAmount: Price,
    val currency: String,
)

data class PaymentCaptureCommand(
    val orderId: Long,
    val providerTransactionId: String,
    val amount: Price,
    val currency: String,
)

data class PaymentRefundRequest(
    val orderId: Long,
    val afterSaleId: Long,
    val items: List<PaymentRefundItem>,
    val amount: Price,
)

class PaymentApplicationService(
    private val repository: PaymentOrderRepository,
    private val sequence: SnowFlakSequence,
    private val publisher: DomainEventPublisher,
) : PaymentUseCase {
    override fun createForOrder(request: PaymentOrderRequest): Result<PaymentOrder, BusinessError> {
        repository.findByOrderId(request.orderId)?.let { existing ->
            return if (
                existing.merchantId == request.merchantId &&
                    existing.payableAmount == request.payableAmount &&
                    existing.currency == request.currency
            )
                Success(existing)
            else Failure(PaymentErrors.ORDER_CONFLICT)
        }
        val payment =
            PaymentOrderImpl(
                id = PaymentOrderId(sequence.nextId()),
                orderId = request.orderId,
                merchantId = request.merchantId,
                payableAmount = request.payableAmount,
                currency = request.currency,
            )
        repository.save(payment)
        payment.publishPendingEvents(publisher)
        return Success(payment)
    }

    override fun getByOrderId(orderId: Long): Result<PaymentOrder, BusinessError> =
        repository.findByOrderId(orderId)?.let(::Success) ?: Failure(PaymentErrors.ORDER_NOT_FOUND)

    override fun getByRefundId(refundId: PaymentRefundId): Result<PaymentOrder, BusinessError> =
        repository.findByRefundId(refundId)?.let(::Success)
            ?: Failure(PaymentErrors.REFUND_NOT_FOUND)

    override fun capture(
        command: PaymentCaptureCommand,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> {
        val payment =
            repository.findByOrderId(command.orderId)
                ?: return Failure(PaymentErrors.ORDER_NOT_FOUND)
        val changed =
            payment.capture(
                command.providerTransactionId,
                command.amount,
                command.currency,
                occurredAt,
            )
        changed.onFailure {
            return Failure(it)
        }
        if (changed.getOrThrow()) persistAndPublish(payment)
        return changed
    }

    override fun requestRefund(
        request: PaymentRefundRequest,
        occurredAt: Instant,
    ): Result<PaymentRefundId, BusinessError> {
        val payment =
            repository.findByOrderId(request.orderId)
                ?: return Failure(PaymentErrors.ORDER_NOT_FOUND)
        payment.refunds
            .firstOrNull { it.afterSaleId == request.afterSaleId }
            ?.let {
                return Success(it.id)
            }
        val refund =
            PaymentRefund(
                id = PaymentRefundId(sequence.nextId()),
                afterSaleId = request.afterSaleId,
                items = request.items,
                amount = request.amount,
                requestedAt = occurredAt,
            )
        payment.requestRefund(refund, occurredAt).onFailure {
            return Failure(it)
        }
        persistAndPublish(payment)
        return Success(refund.id)
    }

    override fun retryRefund(
        refundId: PaymentRefundId,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> =
        mutateRefund(refundId) { it.retryRefund(refundId, occurredAt) }

    override fun markRefundSucceeded(
        refundId: PaymentRefundId,
        providerRefundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> =
        mutateRefund(refundId) {
            it.markRefundSucceeded(refundId, providerRefundId, occurredAt)
        }

    override fun markRefundFailed(
        refundId: PaymentRefundId,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> =
        mutateRefund(refundId) {
            it.markRefundFailed(refundId, reason, occurredAt)
        }

    private fun mutateRefund(
        refundId: PaymentRefundId,
        mutation: (PaymentOrder) -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> {
        val payment =
            repository.findByRefundId(refundId) ?: return Failure(PaymentErrors.REFUND_NOT_FOUND)
        val changed = mutation(payment)
        changed.onFailure {
            return Failure(it)
        }
        if (changed.getOrThrow()) persistAndPublish(payment)
        return changed
    }

    private fun persistAndPublish(payment: PaymentOrder) {
        repository.save(payment)
        payment.publishPendingEvents(publisher)
    }
}
