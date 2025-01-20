package com.jstore.order.domain.saleorder

import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory

class MockSaleOrderCreatedEventListener : DomainEventListener {
    private var domainEventRegistry: DomainEventRegistry? = null

    companion object {
        private const val NAME = "mockSaleOrderCreatedEventListener"
        val log: Logger = LoggerFactory.getLogger(MockSaleOrderCreatedEventListener::class)
    }

    override fun name(): String = NAME
    override fun async(): Boolean = true
    override fun onTopics(): List<String> = listOf(saleOrderTopic)

    override fun handle(event: DomainEvent) {
        when (event) {
            is SaleOrderCreatedEvent -> {
                log.info("order ${event.order.id().value} has been created")
            }
        }
    }

    fun register(domainEventRegistry: DomainEventRegistry) {
        this.domainEventRegistry?.let { throw CommonErrors.ILLEGAL_STATE.msg("已经注册过了") }
        domainEventRegistry.register(this)
        this.domainEventRegistry = domainEventRegistry
    }

    fun logout() {
        domainEventRegistry?.let {
            it.logout(this)
            domainEventRegistry = null
            return
        }
        throw CommonErrors.ILLEGAL_STATE.msg("未注册")
    }
}