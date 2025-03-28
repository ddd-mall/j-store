package com.jstore.order.domain.stock

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.saleorder.SaleOrderCreatedEvent
import org.springframework.context.ApplicationEvent
import org.springframework.core.ResolvableType
import org.springframework.stereotype.Component

@Component
class PreDeductWhenOrderCreatedPolicy(
    private val stockPreDeductHandler: StockPreDeductHandler,
) : DomainEventListener {


    override fun supportsAsyncExecution() = false
    override fun supportsEventType(eventType: ResolvableType): Boolean {
        return eventType.type == SaleOrderCreatedEvent::class.java
    }

    override fun onDomainEvent(event: DomainEvent) {
        when (event) {
            is SaleOrderCreatedEvent -> {
                val preDeductCmd = StockPreDeductCmd(
                    orderId = event.order.id,
                    goodsIdsQuantityMap = event.order.orderItems.associate { GoodsId(it.spuId, it.skuId) to it.quantity })
                stockPreDeductHandler.handle(preDeductCmd)
            }
        }

    }
}