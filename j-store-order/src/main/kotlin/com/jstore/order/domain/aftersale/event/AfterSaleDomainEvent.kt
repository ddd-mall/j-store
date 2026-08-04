package com.jstore.order.domain.aftersale.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import java.time.Instant
import java.util.UUID

data class AfterSaleEventItem(val orderItemId: OrderItemId, val skuId: Long, val quantity: Int, val amount: Price, val currency: String)

sealed class AfterSaleDomainEvent(
    open val afterSaleId: AfterSaleId,
    open val orderId: OrderId,
    override val occurredAt: Instant,
    override val eventId: String,
) : ExplicitDomainEvent {
    override val source: Any get() = afterSaleId
    override val aggregateType = "AfterSale"
    override val aggregateId get() = afterSaleId.value.toString()
    override val eventName get() = this::class.java.getAnnotation(DomainEventType::class.java).name
    override val eventVersion get() = this::class.java.getAnnotation(DomainEventType::class.java).version
}

@DomainEventType(name = "after-sale.requested", version = 1)
data class AfterSaleRequestedEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val applicantId: ApplicantActorId, val items: List<AfterSaleEventItem>, val reason: RefundReason, val requireReturn: Boolean, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
@DomainEventType(name = "after-sale.approved", version = 1)
data class AfterSaleApprovedEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val merchantId: MerchantActorId, val items: List<AfterSaleEventItem>, val requireReturn: Boolean, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
@DomainEventType(name = "after-sale.return-received", version = 1)
data class AfterSaleReturnReceivedEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val merchantId: MerchantActorId, val items: List<AfterSaleEventItem>, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
@DomainEventType(name = "after-sale.refund-requested", version = 1)
data class AfterSaleRefundRequestedEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val merchantId: MerchantActorId, val items: List<AfterSaleEventItem>, val amount: Price, val currency: String, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
@DomainEventType(name = "after-sale.refund-succeeded", version = 1)
data class AfterSaleRefundSucceededEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val refundId: String, val items: List<AfterSaleEventItem>, val amount: Price, val currency: String, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
@DomainEventType(name = "after-sale.refund-failed", version = 1)
data class AfterSaleRefundFailedEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val refundId: String, val reason: String, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
@DomainEventType(name = "after-sale.rejected", version = 1)
data class AfterSaleRejectedEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val merchantId: MerchantActorId, val rejectionReason: String, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
@DomainEventType(name = "after-sale.cancelled", version = 1)
data class AfterSaleCancelledEvent(override val afterSaleId: AfterSaleId, override val orderId: OrderId, val applicantId: ApplicantActorId, override val occurredAt: Instant, override val eventId: String = UUID.randomUUID().toString()) : AfterSaleDomainEvent(afterSaleId, orderId, occurredAt, eventId)
