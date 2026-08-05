package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.contracts.commerce.*
import com.jstore.order.domain.aftersale.event.AfterSaleRefundSucceededEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.event.OrderCancelledEvent
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.event.OrderPaidEvent
import org.springframework.stereotype.Component

/**
 * 事件翻译器：订单领域事件 → 库存 ACL 集成事件
 *
 * 职责：纯格式转换，不包含任何业务逻辑 位于 boot 组装层，是两个限界上下文之间的桥梁
 */
@Component
class OrderCreatedToStockReservationTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<OrderCreatedEvent> {
    override fun listenerId(): String = "translator.order-created.to-stock-reservation-requested"

    override fun onDomainEvent(event: OrderCreatedEvent) {
        integrationMessagePublisher.publish(
            ReserveInventoryCommand(
                orderId = event.orderId.value,
                items = event.items.map { ContractItem(skuId = it.skuId, quantity = it.quantity) },
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
    }
}

@Component
class AfterSaleRefundSucceededToStockRestoreTranslator(
    private val integrationMessagePublisher: IntegrationMessagePublisher
) : DomainEventListener<AfterSaleRefundSucceededEvent> {
    override fun listenerId(): String =
        "translator.after-sale-refund-succeeded.to-stock-restore-requested.v1"

    override fun onDomainEvent(event: AfterSaleRefundSucceededEvent) {
        val restoreItems = event.items.map { ContractItem(it.skuId, it.quantity) }
        integrationMessagePublisher.publish(
            RestoreInventoryAfterRefundCommand(
                afterSaleId = event.afterSaleId.value,
                orderId = event.orderId.value,
                items = restoreItems,
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}
