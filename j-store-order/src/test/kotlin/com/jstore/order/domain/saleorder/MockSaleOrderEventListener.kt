package com.jstore.order.domain.saleorder

import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.context.ApplicationListener

class MockSaleOrderCreatedEventListener : DomainEventListener<SaleOrderCreatedEvent>,
    ApplicationListener<SaleOrderCreatedEvent> {
    private var domainEventRegistry: DomainEventRegistry? = null

    companion object {
        val log: Logger = LoggerFactory.getLogger(MockSaleOrderCreatedEventListener::class)
    }

    override fun supportsAsyncExecution(): Boolean = true
    override fun onApplicationEvent(event: SaleOrderCreatedEvent) {
        log.info("order ${event.order.id().value} has been created")
    }

    fun register(domainEventRegistry: DomainEventRegistry) {
        this.domainEventRegistry?.let { throw CommonErrors.ILLEGAL_STATE.msg("已经注册过了") }
        domainEventRegistry.register(this)
        this.domainEventRegistry = domainEventRegistry
    }

}