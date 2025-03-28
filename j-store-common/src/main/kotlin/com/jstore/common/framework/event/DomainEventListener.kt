package com.jstore.common.framework.event

import org.springframework.core.ResolvableType

interface DomainEventListener {
    fun supportsAsyncExecution() = false
    fun supportsEventType(eventType: ResolvableType): Boolean
    fun onDomainEvent(event: DomainEvent)

}