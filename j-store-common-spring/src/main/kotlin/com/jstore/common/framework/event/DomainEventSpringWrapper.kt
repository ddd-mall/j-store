package com.jstore.common.framework.event

import org.springframework.context.ApplicationEvent

class DomainEventSpringWrapper(
    val domainEvent: DomainEvent,
) : ApplicationEvent(domainEvent.source)