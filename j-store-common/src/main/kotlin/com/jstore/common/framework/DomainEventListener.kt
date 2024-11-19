package com.jstore.common.framework

interface DomainEventListener<T: DomainEvent> {
    val topic: Class<T>
    fun handle(event: T)
}