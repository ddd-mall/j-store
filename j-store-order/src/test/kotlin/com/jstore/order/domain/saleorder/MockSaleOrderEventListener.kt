package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.context.ApplicationEvent
import org.springframework.core.ResolvableType

class MockSaleOrderCreatedEventListener : DomainEventListener {
    private val log: Logger = LoggerFactory.getLogger(MockSaleOrderCreatedEventListener::class)


    override fun supportsEventType(eventType: ResolvableType): Boolean {
        return true
    }

    override fun onApplicationEvent(event: ApplicationEvent) {
        when (event) {
            is SaleOrderCreatedEvent -> {
                log.info("order ${event.order.id().value} has been created")
            }
        }
    }


}