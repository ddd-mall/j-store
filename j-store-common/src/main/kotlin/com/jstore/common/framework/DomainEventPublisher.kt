package com.jstore.common.framework

interface DomainEventPublisher {
    fun <T: DomainEvent> publishEvent(event: T)
}