package com.jstore.common.framework.event

interface DomainEventConsumptionRepository {
    fun tryStart(listenerId: String, event: DomainEvent): Boolean
}

object NoopDomainEventConsumptionRepository : DomainEventConsumptionRepository {
    override fun tryStart(listenerId: String, event: DomainEvent): Boolean = true
}
