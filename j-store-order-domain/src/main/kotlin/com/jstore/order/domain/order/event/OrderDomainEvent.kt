package com.jstore.order.domain.order.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.SaleAuthorizationRef
import java.time.Instant

sealed class OrderDomainEvent(
    open val orderId: OrderId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String,
    override val eventName: String,
    override val eventVersion: Int,
) : DomainEvent {

    override val aggregateType: String = "Order"
    override val aggregateId: String
        get() = orderId.value.toString()
}

data class OrderItemSnapshot(
    val spuId: Long,
    val skuId: Long,
    val quantity: Int,
    val catalogSnapshotVersion: Long,
    val unitPrice: Price,
    val offerId: Long = skuId,
    val storeId: Long = 1,
    val offerVersion: Long = 1,
    val fulfillmentNodeId: String = "DEFAULT",
    val channelId: String = "ONLINE",
)

@DomainEventType(name = "order.created", version = 4)
data class OrderCreatedEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val payableAmount: Price,
    val currency: String,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.created", 4)

@DomainEventType(name = "order.sale-authorized", version = 1)
data class OrderSaleAuthorizedEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val authorizations: List<SaleAuthorizationRef>,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.sale-authorized", 1)

@DomainEventType(name = "order.stock-confirmed", version = 1)
data class OrderStockConfirmedEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val payableAmount: Price,
    val currency: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.stock-confirmed", 1)

@DomainEventType(name = "order.paid", version = 2)
data class OrderPaidEvent(
    override val orderId: OrderId,
    val merchantId: MerchantId,
    val paymentReference: String,
    val paidAmount: Price,
    val currency: String,
    val items: List<OrderItemSnapshot>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.paid", 2)

@DomainEventType(name = "order.completed")
data class OrderCompletedEvent(
    override val orderId: OrderId,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.completed", 1)

@DomainEventType(name = "order.cancelled")
data class OrderCancelledEvent(
    override val orderId: OrderId,
    val reason: String = "",
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : OrderDomainEvent(orderId, occurredAt, eventId, "order.cancelled", 1)
