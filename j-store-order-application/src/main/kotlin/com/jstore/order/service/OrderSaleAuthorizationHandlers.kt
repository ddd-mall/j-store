package com.jstore.order.service

import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.SaleAuthorizationFailedIntegrationEvent
import com.jstore.contracts.commerce.SaleAuthorizedIntegrationEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.SaleAuthorizationRef

class SaleAuthorizedOrderHandler(private val orders: OrderUseCase) :
    IntegrationMessageHandler<SaleAuthorizedIntegrationEvent> {
    override fun handlerId() = "order.record-sale-authorized.v1"

    override fun handle(message: SaleAuthorizedIntegrationEvent) {
        orders
            .recordSaleAuthorized(
                OrderId(message.orderId),
                message.items.map {
                    SaleAuthorizationRef(it.authorizationId, it.offerId, it.expiresAt)
                },
            )
            .onFailure { throw IllegalStateException(it.message) }
    }
}

class SaleAuthorizationFailedOrderHandler(private val orders: OrderUseCase) :
    IntegrationMessageHandler<SaleAuthorizationFailedIntegrationEvent> {
    override fun handlerId() = "order.close-on-sale-authorization-failed.v1"

    override fun handle(message: SaleAuthorizationFailedIntegrationEvent) {
        orders
            .markSaleAuthorizationFailed(OrderId(message.orderId), message.reason)
            .onFailure { throw IllegalStateException(it.message) }
    }
}
