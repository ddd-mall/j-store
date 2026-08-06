package com.jstore.shop.domain.offer.event

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.shop.domain.offer.SaleAuthorizationId
import java.time.Instant

@DomainEventType(name = "store.sale-authorized", version = 1)
data class AuthorizedSaleLine(
    val authorizationId: String,
    val offerId: Long,
    val skuId: Long,
    val quantity: Int,
    val fulfillmentNodeId: String,
    val expiresAt: Instant,
)

data class SaleAuthorizedEvent(
    val orderId: Long,
    val items: List<AuthorizedSaleLine>,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "store.sale-authorized"
    override val eventVersion = 1
    override val aggregateType = "OrderSaleAuthorization"
    override val aggregateId = orderId.toString()
}

@DomainEventType(name = "store.sale-authorization-released", version = 1)
data class SaleAuthorizationReleasedEvent(
    val authorizationId: SaleAuthorizationId,
    val orderId: Long,
    override val occurredAt: Instant,
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "store.sale-authorization-released"
    override val eventVersion = 1
    override val aggregateType = "SaleAuthorization"
    override val aggregateId = authorizationId.value
}

@DomainEventType(name = "store.sale-authorization-rejected", version = 1)
data class SaleAuthorizationRejectedEvent(
    val orderId: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "store.sale-authorization-rejected"
    override val eventVersion = 1
    override val aggregateType = "OrderSaleAuthorization"
    override val aggregateId = orderId.toString()
}
