package com.jstore.order.stock

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import com.jstore.order.risk.VerifyRiskWhenSaleOrderCreatePolicy
import com.jstore.order.saleorder.OrderType
import com.jstore.order.saleorder.SaleOrderCreatedEvent
import com.jstore.order.saleorder.saleOrderTopic
import org.springframework.stereotype.Component

@Component
class PreDeductWhenNormalOrderCreatedPolicy(
    private val stockDeductCmdHandler: StockDeductCmdHandler,
    domainEventRegistry: DomainEventRegistry
) : DomainEventListener {
    init {
        domainEventRegistry.register(this)
    }
    override fun name(): String = this::class.qualifiedName!!
    override fun onTopics(): List<String> = listOf(saleOrderTopic)
    override fun async(): Boolean = false
    override fun handle(event: DomainEvent) {
        while (true) {
            if (event.successesOn().none { it == VerifyRiskWhenSaleOrderCreatePolicy::class.qualifiedName }) {
                event.mutex.wait()
            } else {
                break
            }
        }
        when (event) {
            is SaleOrderCreatedEvent -> {
                when (event.orderType) {
                    OrderType.SEC_KILL, OrderType.GROUP, OrderType.NORMAL -> {
                        stockDeductCmdHandler.handle(StockDeductCmd(orderId = event.orderId))
                    }
                    OrderType.PRE_SELL -> TODO()
                }

            }
        }
    }
}