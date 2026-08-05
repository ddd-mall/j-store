package com.jstore.common.framework.event

interface DomainEventConsumptionRepository {
    fun tryStart(
        consumerId: String,
        messageId: String,
        messageName: String,
        messageVersion: Int,
    ): Boolean

    fun tryStart(listenerId: String, event: DomainEvent): Boolean {
        val metadata = event.metadata
        return tryStart(
            listenerId,
            metadata.eventId,
            metadata.eventName,
            metadata.eventVersion,
        )
    }
}

object NoopDomainEventConsumptionRepository : DomainEventConsumptionRepository {
    override fun tryStart(
        consumerId: String,
        messageId: String,
        messageName: String,
        messageVersion: Int,
    ): Boolean = true
}
