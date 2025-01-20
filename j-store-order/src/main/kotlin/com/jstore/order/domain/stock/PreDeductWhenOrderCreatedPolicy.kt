package com.jstore.order.domain.stock

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.saleorder.SaleOrderCreatedEvent
import com.jstore.order.domain.saleorder.saleOrderTopic
import org.springframework.stereotype.Component

@Component
class PreDeductWhenOrderCreatedPolicy(
    private val stockPreDeductHandler: StockPreDeductHandler,
    domainEventRegistry: DomainEventRegistry
) : DomainEventListener {
    init {
        domainEventRegistry.register(this)
    }

    override fun name(): String {
        return this::class.simpleName!!
    }

    override fun onTopics(): List<String> {
        return listOf(saleOrderTopic)
    }

    override fun async(): Boolean {
        return false
    }

    override fun handle(event: DomainEvent) {
        when (event) {
            is SaleOrderCreatedEvent -> {
                val preDeductCmd = StockPreDeductCmd(
                    orderId = event.order.id(),
                    goodsIdsQuantityMap = event.order.orderItems.associate { GoodsId(it.spuId, it.skuId) to it.count })
                stockPreDeductHandler.handle(preDeductCmd)
            }
        }
    }
}