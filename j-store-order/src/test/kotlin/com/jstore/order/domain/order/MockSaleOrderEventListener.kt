package com.jstore.order.domain.order

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.domain.order.event.OrderCreatedEvent
import org.springframework.core.ResolvableType

class MockOrderCreatedEventListener : DomainEventListener<OrderCreatedEvent> {
    private val log: Logger = LoggerFactory.getLogger(MockOrderCreatedEventListener::class)


    override fun supportsEventType(eventType: ResolvableType): Boolean {
        return eventType.type.typeName == OrderCreatedEvent::class.java.typeName
    }

    override fun onDomainEvent(event: DomainEvent) {
        when (event) {
            is OrderCreatedEvent -> {
                log.info("order ${event.orderId.value} has been created")
            }
        }
    }


}