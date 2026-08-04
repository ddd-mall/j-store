package com.jstore.common.framework.event

/**
 * 领域事件消息总线。
 *
 * 仅负责本进程内事件分发，不提供事务一致性或可靠投递保证。 业务代码需要事务性发布时应依赖 DomainEventPublisher。
 */
interface DomainEventBus {
    fun publishEvent(domainEvent: DomainEvent)

    fun register(domainEventListener: DomainEventListener<*>)

    fun unregister(domainEventListener: DomainEventListener<*>)
}
