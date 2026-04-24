package com.jstore.translator

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.goods.domain.inventory.event.StockReservationFailedEvent
import com.jstore.goods.domain.inventory.event.StockReservedEvent
import com.jstore.order.acl.event.OrderStockConfirmedEvent
import com.jstore.order.acl.event.OrderStockInsufficientEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * 事件翻译器：库存领域事件 → 订单 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑
 */
@Component
class StockToOrderEventTranslator(
    private val domainEventPublisher: DomainEventPublisher,
) {

    @EventListener
    fun onStockReserved(event: StockReservedEvent) {
        domainEventPublisher.publishEvent(
            OrderStockConfirmedEvent(orderId = event.orderId)
        )
    }

    @EventListener
    fun onStockReservationFailed(event: StockReservationFailedEvent) {
        domainEventPublisher.publishEvent(
            OrderStockInsufficientEvent(orderId = event.orderId, reason = event.reason)
        )
    }
}
