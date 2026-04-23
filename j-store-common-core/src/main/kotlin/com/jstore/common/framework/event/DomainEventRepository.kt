package com.jstore.common.framework.event

interface DomainEventRepository {
    fun saveAll(domainEvents: Iterable<DomainEvent>)
    fun save(domainEvent: DomainEvent)
}