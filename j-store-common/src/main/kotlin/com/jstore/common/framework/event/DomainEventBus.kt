package com.jstore.common.framework.event

/**
 * 领域事件消息总线
 */
interface DomainEventBus {
    fun publishEvent(domainEvent: DomainEvent)
    fun register(domainEventListener: DomainEventListener)
    fun unregister(domainEventListener: DomainEventListener)
}
