package com.jstore.common.framework.event

import org.springframework.context.ApplicationEventPublisher

class SpringDomainEventBus(
    private val registry: SpringDomainEventListenerRegistry,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventBus {

    override fun publishEvent(domainEvent: DomainEvent) {
        applicationEventPublisher.publishEvent(domainEvent)
    }

    override fun register(domainEventListener: DomainEventListener<*>) {
        registry.register(domainEventListener)
    }

    override fun unregister(domainEventListener: DomainEventListener<*>) {
        registry.unregister(domainEventListener)
    }


}
