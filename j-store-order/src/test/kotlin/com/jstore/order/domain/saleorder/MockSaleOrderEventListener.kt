package com.jstore.order.domain.saleorder

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.core.ResolvableType

class MockSaleOrderCreatedEventListener : DomainEventListener {
    private val log: Logger = LoggerFactory.getLogger(MockSaleOrderCreatedEventListener::class)


    override fun supportsEventType(eventType: ResolvableType): Boolean {
        return eventType.type.typeName == SaleOrderCreatedEvent::class.java.typeName
    }

    override fun onDomainEvent(event: DomainEvent) {
        when (event) {
            is SaleOrderCreatedEvent -> {
                log.info("order ${event.order.id.value} has been created")
            }
        }
    }


}