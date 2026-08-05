package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.contracts.commerce.InventoryReservationFailedIntegrationEvent
import com.jstore.contracts.commerce.InventoryReservedIntegrationEvent
import com.jstore.goods.domain.inventory.event.StockReservationFailedEvent
import com.jstore.goods.domain.inventory.event.StockReservedEvent
import org.springframework.stereotype.Component

/**
 * 事件翻译器：库存领域事件 → 订单 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑
 */
@Component
class StockReservedToOrderConfirmedTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<StockReservedEvent> {
    override fun listenerId(): String = "translator.stock-reserved.to-order-stock-confirmed"

    override fun onDomainEvent(event: StockReservedEvent) {
        integrationMessagePublisher.publish(
            InventoryReservedIntegrationEvent(
                event.orderId,
                event.eventId,
                event.occurredAt,
            )
        )
    }
}

@Component
class StockReservationFailedToOrderInsufficientTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<StockReservationFailedEvent> {
    override fun listenerId(): String =
        "translator.stock-reservation-failed.to-order-stock-insufficient"

    override fun onDomainEvent(event: StockReservationFailedEvent) {
        integrationMessagePublisher.publish(
            InventoryReservationFailedIntegrationEvent(
                event.orderId,
                event.reason,
                event.eventId,
                event.occurredAt,
            )
        )
    }
}
