package com.jstore.common.framework.event

import org.springframework.context.ApplicationEventPublisher

class SpringLocalDomainEventBus(
    private val registry: SpringDomainEventListenerRegistry,
    private val applicationEventPublisher: ApplicationEventPublisher,
) : LocalDomainEventBus {

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
