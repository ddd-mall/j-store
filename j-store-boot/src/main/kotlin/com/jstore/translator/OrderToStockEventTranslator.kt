package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.goods.acl.event.ConfirmItem
import com.jstore.goods.acl.event.ReleaseItem
import com.jstore.goods.acl.event.ReservationItem
import com.jstore.goods.acl.event.StockConfirmRequestedEvent
import com.jstore.goods.acl.event.StockReleaseRequestedEvent
import com.jstore.goods.acl.event.StockReservationRequestedEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.event.OrderCancelledEvent
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderRefundApprovedEvent
import org.springframework.stereotype.Component

/**
 * 事件翻译器：订单领域事件 → 库存 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑
 * 位于 boot 组装层，是两个限界上下文之间的桥梁
 */
@Component
class OrderCreatedToStockReservationTranslator(
    private val domainEventPublisher: DomainEventPublisher,
) : DomainEventListener<OrderCreatedEvent> {
    override fun listenerId(): String = "translator.order-created.to-stock-reservation-requested"

    override fun onDomainEvent(event: OrderCreatedEvent) {
        domainEventPublisher.publishEvent(
            StockReservationRequestedEvent(
                orderId = event.orderId.value,
                items = event.items.map { ReservationItem(skuId = it.skuId, quantity = it.quantity) }
            )
        )
    }
}

@Component
class OrderPaidToStockConfirmTranslator(
    private val domainEventPublisher: DomainEventPublisher,
) : DomainEventListener<OrderPaidEvent> {
    override fun listenerId(): String = "translator.order-paid.to-stock-confirm-requested"

    override fun onDomainEvent(event: OrderPaidEvent) {
        domainEventPublisher.publishEvent(
            StockConfirmRequestedEvent(
                orderId = event.orderId.value,
                items = event.items.map { ConfirmItem(skuId = it.skuId) }
            )
        )
    }
}

@Component
class OrderCancelledToStockReleaseTranslator(
    private val domainEventPublisher: DomainEventPublisher,
    private val orderRepository: OrderRepository,
) : DomainEventListener<OrderCancelledEvent> {
    override fun listenerId(): String = "translator.order-cancelled.to-stock-release-requested"

    override fun onDomainEvent(event: OrderCancelledEvent) {
        val order = orderRepository.findById(OrderId(event.orderId.value)) ?: return
        domainEventPublisher.publishEvent(
            StockReleaseRequestedEvent(
                orderId = event.orderId.value,
                items = order.items.map { ReleaseItem(skuId = it.skuId) }
            )
        )
    }
}

@Component
class OrderRefundApprovedToStockReleaseTranslator(
    private val domainEventPublisher: DomainEventPublisher,
    private val orderRepository: OrderRepository,
) : DomainEventListener<OrderRefundApprovedEvent> {
    override fun listenerId(): String = "translator.order-refund-approved.to-stock-release-requested"

    override fun onDomainEvent(event: OrderRefundApprovedEvent) {
        // 已发货的退款需要走退货流程，不直接释放库存
        if (event.requireReturn) return

        val order = orderRepository.findById(OrderId(event.orderId.value)) ?: return
        val approvedItemIds = event.approvedItemIds.toSet()
        val releaseItems = order.items
            .filter { it.id in approvedItemIds }
            .map { ReleaseItem(skuId = it.skuId) }
        domainEventPublisher.publishEvent(
            StockReleaseRequestedEvent(
                orderId = event.orderId.value,
                items = releaseItems
            )
        )
    }
}
