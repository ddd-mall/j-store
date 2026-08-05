package com.jstore.common.framework.event

/**
 * 本进程领域事件总线。
 *
 * 只负责同步调用当前进程内的领域事件监听器，不表示远程消息投递，也不提供事务一致性或可靠投递保证。 事务性发布由 [DomainEventPublisher] 和 Outbox 基础设施负责。
 */
interface LocalDomainEventBus {
    fun publishEvent(domainEvent: DomainEvent)

    fun register(domainEventListener: DomainEventListener<*>)

    fun unregister(domainEventListener: DomainEventListener<*>)
}
