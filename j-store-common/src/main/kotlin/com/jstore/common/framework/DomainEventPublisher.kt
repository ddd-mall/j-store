package com.jstore.common.framework

interface DomainEventPublisher<T: DomainEvent> {
    fun publishEvent(event: T)
}