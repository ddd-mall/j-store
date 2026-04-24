package com.jstore.common.framework.event

import org.springframework.context.ApplicationEventPublisher


class SpringDomainEventDispatcher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventDispatcher {


    override fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener<*>>) {
        applicationEventPublisher.publishEvent(domainEvent)
    }
}