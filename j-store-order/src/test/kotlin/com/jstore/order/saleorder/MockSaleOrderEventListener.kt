package com.jstore.order.saleorder

import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.SimpleDomainEventRegistry
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory

class MockSaleOrderCreatedEventListener : DomainEventListener {
    private var domainEventRegistry: SimpleDomainEventRegistry? = null


    companion object {
        val log: Logger = LoggerFactory.getLogger(MockSaleOrderCreatedEventListener::class)
    }

    override fun topics(): List<String> {
        return listOf(saleOrderTopic)
    }

    override fun handle(event: DomainEvent) {
        when (event) {
            is NormalSaleOrderCreatedEvent -> {
                log.info("order ${event.saleOrderId} has been created")
            }
        }
    }

    fun register(domainEventRegistry: SimpleDomainEventRegistry) {
        this.domainEventRegistry?.let { throw CommonErrors.ILLEGAL_STATE.to("已经注册过了") }
        domainEventRegistry.register(this)
        this.domainEventRegistry = domainEventRegistry
    }

    fun logout() {
        domainEventRegistry?.let {
            it.logout(this)
            domainEventRegistry = null
            return
        }
        throw CommonErrors.ILLEGAL_STATE.to("未注册")
    }
}