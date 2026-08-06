package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.contracts.commerce.*
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.event.OrderCancelledEvent
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderSaleAuthorizedEvent
import org.springframework.stereotype.Component

/**
 * 事件翻译器：订单领域事件 → 库存 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑 位于 boot 组装层，是两个限界上下文之间的桥梁
 */
@Component
class OrderCreatedToSaleAuthorizationTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderCreatedEvent> {
    override fun listenerId(): String = "translator.order-created.to-sale-authorization.v1"

    override fun onDomainEvent(event: OrderCreatedEvent) {
        integrationMessagePublisher.publish(
            AuthorizeSaleCommand(
                orderId = event.orderId.value,
                items =
                    event.items.map {
                        ContractSaleItem(
                            offerId = it.offerId,
                            storeId = it.storeId,
                            spuId = it.spuId,
                            skuId = it.skuId,
                            quantity = it.quantity,
                            catalogSnapshotVersion = it.catalogSnapshotVersion,
                            offerVersion = it.offerVersion,
                            fulfillmentNodeId = it.fulfillmentNodeId,
                            channelId = it.channelId,
                            unitPriceFen = it.unitPrice.fen,
                        )
                    },
                sourceMessageId = event.eventId,
                merchantId = event.merchantId.value,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderSaleAuthorizedToStockReservationTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderSaleAuthorizedEvent> {
    override fun listenerId(): String = "translator.order-sale-authorized.to-atp-reservation.v1"

    override fun onDomainEvent(event: OrderSaleAuthorizedEvent) {
        val authorizationByOffer = event.authorizations.associateBy { it.offerId }
        require(authorizationByOffer.size == event.items.size)
        integrationMessagePublisher.publish(
            ReserveInventoryCommand(
                orderId = event.orderId.value,
                items =
                    event.items.map { item ->
                        val authorization = authorizationByOffer.getValue(item.offerId)
                        ContractAuthorizedSaleItem(
                            authorizationId = authorization.authorizationId,
                            offerId = item.offerId,
                            skuId = item.skuId,
                            quantity = item.quantity,
                            fulfillmentNodeId = item.fulfillmentNodeId,
                            expiresAt = authorization.expiresAt,
                        )
                    },
                sourceMessageId = event.eventId,
                merchantId = event.merchantId.value,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderPaidToStockConfirmTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId(): String = "translator.order-paid.to-stock-confirm-requested"

    override fun onDomainEvent(event: OrderPaidEvent) {
        integrationMessagePublisher.publish(
            ConfirmInventoryCommand(
                orderId = event.orderId.value,
                items = event.items.map { ContractItem(skuId = it.skuId, quantity = it.quantity) },
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class OrderCancelledToStockReleaseTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher,
    private val orderRepository: OrderRepository,
) : DomainEventListener<OrderCancelledEvent> {
    override fun listenerId(): String = "translator.order-cancelled.to-stock-release-requested"

    override fun onDomainEvent(event: OrderCancelledEvent) {
        val order = orderRepository.findById(OrderId(event.orderId.value)) ?: return
        integrationMessagePublisher.publish(
            ReleaseInventoryCommand(
                orderId = event.orderId.value,
                items = order.items.map { ContractItem(skuId = it.skuId, quantity = it.quantity) },
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
        if (order.saleAuthorizations.isNotEmpty()) {
            integrationMessagePublisher.publish(
                ReleaseSaleAuthorizationCommand(
                    orderId = event.orderId.value,
                    authorizationIds = order.saleAuthorizations.map { it.authorizationId },
                    sourceMessageId = event.eventId,
                    occurredAtValue = event.occurredAt,
                )
            )
        }
    }
}
