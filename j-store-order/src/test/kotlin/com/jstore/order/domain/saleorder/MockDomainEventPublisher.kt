package com.jstore.order.domain.saleorder

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventBus
import com.jstore.common.framework.event.DomainEventPublisher

class MockDomainEventPublisher(
    private val domainEventBus: DomainEventBus
) : DomainEventPublisher {

    override fun <T : DomainEvent> publishEvent(event: T) {
        domainEventBus.publishEvent(event)
    }
}