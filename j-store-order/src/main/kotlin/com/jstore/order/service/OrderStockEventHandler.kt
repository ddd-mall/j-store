package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.onFailure
import com.jstore.order.acl.event.OrderStockConfirmedEvent
import com.jstore.order.domain.order.OrderId

/** 订单应用层事件处理器：监听库存确认成功事件，将订单转为待支付 */
class OrderStockConfirmedEventHandler(private val orderService: OrderService) :
    DomainEventListener<OrderStockConfirmedEvent> {
    override fun listenerId(): String = "order.confirm-stock-on-stock-confirmed"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun onDomainEvent(event: OrderStockConfirmedEvent) {
        log.info("库存已确认，订单转为待支付: orderId=${event.orderId}")
        orderService.confirmStock(OrderId(event.orderId)).onFailure {
            log.error("确认订单库存失败: orderId=${event.orderId}, error=${it.message}")
        }
    }
}
