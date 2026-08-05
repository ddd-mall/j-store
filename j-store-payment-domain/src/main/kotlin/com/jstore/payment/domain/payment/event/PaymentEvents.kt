package com.jstore.payment.domain.payment.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.common.properties.Price
import com.jstore.payment.domain.payment.PaymentOrderId
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.domain.payment.PaymentRefundItem
import java.time.Instant

sealed class PaymentDomainEvent(
    open val paymentId: PaymentOrderId,
    override val occurredAt: Instant,
    override val eventId: String,
    override val eventName: String,
    override val eventVersion: Int,
) : DomainEvent {

    override val aggregateType: String = "PaymentOrder"
    override val aggregateId: String
        get() = paymentId.value.toString()

}

@DomainEventType(name = "payment.captured")
data class PaymentCapturedEvent(
    override val paymentId: PaymentOrderId,
    val orderId: Long,
    val merchantId: Long,
    val providerTransactionId: String,
    val amount: Price,
    val currency: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : PaymentDomainEvent(paymentId, occurredAt, eventId, "payment.captured", 1)

@DomainEventType(name = "payment.refund-requested")
data class PaymentRefundRequestedEvent(
    override val paymentId: PaymentOrderId,
    val refundId: PaymentRefundId,
    val orderId: Long,
    val afterSaleId: Long,
    val amount: Price,
    val currency: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : PaymentDomainEvent(paymentId, occurredAt, eventId, "payment.refund-requested", 1)

@DomainEventType(name = "payment.refund-succeeded")
data class PaymentRefundSucceededEvent(
    override val paymentId: PaymentOrderId,
    val refundId: PaymentRefundId,
    val orderId: Long,
    val afterSaleId: Long,
    val merchantId: Long,
    val providerRefundId: String,
    val items: List<PaymentRefundItem>,
    val amount: Price,
    val currency: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : PaymentDomainEvent(paymentId, occurredAt, eventId, "payment.refund-succeeded", 1)

@DomainEventType(name = "payment.refund-failed")
data class PaymentRefundFailedEvent(
    override val paymentId: PaymentOrderId,
    val refundId: PaymentRefundId,
    val orderId: Long,
    val afterSaleId: Long,
    val reason: String,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : PaymentDomainEvent(paymentId, occurredAt, eventId, "payment.refund-failed", 1)
