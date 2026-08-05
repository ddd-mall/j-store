package com.jstore.payment.domain.payment.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.common.properties.Price
import com.jstore.payment.domain.payment.PaymentOrderId
import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.domain.payment.PaymentRefundItem
import java.time.Instant

sealed class PaymentDomainEvent(
    open val paymentId: PaymentOrderId,
    override val occurredAt: Instant,
) : ExplicitDomainEvent {
    override val source: Any
        get() = paymentId

    override val eventName: String
        get() = this::class.java.getAnnotation(DomainEventType::class.java).name

    override val eventVersion: Int
        get() = this::class.java.getAnnotation(DomainEventType::class.java).version

    override val aggregateType: String = "PaymentOrder"
    override val aggregateId: String
        get() = paymentId.value.toString()

    override val eventId: String
        get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
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
) : PaymentDomainEvent(paymentId, occurredAt)

@DomainEventType(name = "payment.refund-requested")
data class PaymentRefundRequestedEvent(
    override val paymentId: PaymentOrderId,
    val refundId: PaymentRefundId,
    val orderId: Long,
    val afterSaleId: Long,
    val amount: Price,
    val currency: String,
    override val occurredAt: Instant,
) : PaymentDomainEvent(paymentId, occurredAt)

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
) : PaymentDomainEvent(paymentId, occurredAt)

@DomainEventType(name = "payment.refund-failed")
data class PaymentRefundFailedEvent(
    override val paymentId: PaymentOrderId,
    val refundId: PaymentRefundId,
    val orderId: Long,
    val afterSaleId: Long,
    val reason: String,
    override val occurredAt: Instant,
) : PaymentDomainEvent(paymentId, occurredAt)
