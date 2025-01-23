package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory

class MockSaleOrderCreatedEventListener : DomainEventListener<SaleOrderCreatedEvent> {
    val log: Logger = LoggerFactory.getLogger(MockSaleOrderCreatedEventListener::class)


    override fun supportsAsyncExecution(): Boolean = true
    override fun onApplicationEvent(event: SaleOrderCreatedEvent) {
        log.info("order ${event.order.id().value} has been created")
    }


}