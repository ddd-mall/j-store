package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.onFailure
import com.jstore.order.acl.event.OrderStockInsufficientEvent
import com.jstore.order.domain.order.OrderId

/**
 * 订单应用层事件处理器：监听库存不足事件，取消订单
 */
class OrderStockInsufficientEventHandler(
    private val orderService: OrderService,
) : DomainEventListener<OrderStockInsufficientEvent> {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun onDomainEvent(event: OrderStockInsufficientEvent) {
        log.warn("库存不足，取消订单: orderId=${event.orderId}, reason=${event.reason}")
        orderService.markStockInsufficient(OrderId(event.orderId), event.reason).onFailure {
            log.error("取消订单失败: orderId=${event.orderId}, error=${it.message}")
        }
    }
}
