package com.jstore.common.framework.event


class SpringDomainEventBus(
    private val registry: SpringDomainEventListenerRegistry,
    private val dispatcher: SpringDomainEventDispatcher,
) : DomainEventBus {

    override fun publishEvent(domainEvent: DomainEvent) {
        dispatcher.dispatch(domainEvent, registry.getListeners())
    }

    override fun register(domainEventListener: DomainEventListener) {
        registry.register(domainEventListener)
    }

    override fun unregister(domainEventListener: DomainEventListener) {
        registry.unregister(domainEventListener)
    }


}