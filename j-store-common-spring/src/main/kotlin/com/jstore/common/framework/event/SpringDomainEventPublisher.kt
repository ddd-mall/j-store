package com.jstore.common.framework.event

import org.springframework.stereotype.Component

@Component
class SpringDomainEventPublisher(
    private val springDomainEventBus: SpringDomainEventBus,
) : DomainEventPublisher {


    override fun <T : DomainEvent> publishEvent(event: T) {
        springDomainEventBus.publishEvent(event)
    }


}