package com.jstore.order.domain.stock

import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.saleorder.SaleOrderCreatedEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

@Component
class PreDeductWhenOrderCreatedPolicy(
    private val stockPreDeductHandler: StockPreDeductHandler,
    domainEventRegistry: DomainEventRegistry?
) : DomainEventListener<SaleOrderCreatedEvent>, ApplicationListener<SaleOrderCreatedEvent> {
    init {
        domainEventRegistry?.register(this)
    }

    override fun supportsAsyncExecution() = false

    override fun onApplicationEvent(event: SaleOrderCreatedEvent) {
        val preDeductCmd = StockPreDeductCmd(
            orderId = event.order.id(),
            goodsIdsQuantityMap = event.order.orderItems.associate { GoodsId(it.spuId, it.skuId) to it.count })
        stockPreDeductHandler.handle(preDeductCmd)
    }
}