package com.jstore.common.framework.event

import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.PayloadApplicationEvent
import org.springframework.stereotype.Component

@Component
class SpringDomainEventDispatcher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventDispatcher {


    override fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener>) {
        applicationEventPublisher.publishEvent(PayloadApplicationEvent(domainEvent.source, domainEvent))
    }
}