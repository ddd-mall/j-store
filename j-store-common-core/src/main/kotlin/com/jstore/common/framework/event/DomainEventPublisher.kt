package com.jstore.common.framework.event

interface DomainEventPublisher {
    fun <T: DomainEvent> publishEvent(event: T)
}