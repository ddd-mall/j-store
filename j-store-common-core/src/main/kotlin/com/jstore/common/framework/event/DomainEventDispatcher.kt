package com.jstore.common.framework.event

interface DomainEventDispatcher {
    fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener<*>>)
}