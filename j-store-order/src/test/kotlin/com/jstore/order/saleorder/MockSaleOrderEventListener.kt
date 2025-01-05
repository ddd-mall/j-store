package com.jstore.order.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.SimpleDomainEventRegistry
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory

class MockSaleOrderCreatedEventListener : DomainEventListener {


    companion object {
        val log: Logger = LoggerFactory.getLogger(MockSaleOrderCreatedEventListener::class)
    }

    override fun topics(): List<String> {
        return listOf(saleOrderTopic)
    }

    override fun handle(event: DomainEvent) {
        when (event) {
            is SaleOrderCreatedEvent -> {
                log.info("order ${event.saleOrderId} has been created")
            }
        }
    }

    fun register() {
        SimpleDomainEventRegistry.getDefaultInstance().register(this)
    }

}