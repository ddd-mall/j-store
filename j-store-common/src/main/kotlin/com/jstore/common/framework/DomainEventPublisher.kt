package com.jstore.common.framework

interface DomainEventPublisher {
    fun publish(event: DomainEvent)
}