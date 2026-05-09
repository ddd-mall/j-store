package com.jstore.common.framework.event

interface DomainEventListenerRegistry {
    fun register(listener: DomainEventListener<*>)

    fun unregister(listener: DomainEventListener<*>)

    fun getListeners(): List<DomainEventListener<*>>
}