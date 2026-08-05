package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.LocalDomainEventBus

class LocalDomainEventDeliveryChannel(
    private val eventSerializer: EventSerializer,
    private val localDomainEventBus: LocalDomainEventBus,
) : OutboxDeliveryChannel {
    override val target: OutboxDeliveryTarget = OutboxDeliveryTarget.LOCAL_DOMAIN

    override fun deliver(entry: OutboxEntry) {
        check(entry.messageKind == OutboxMessageKind.DOMAIN_EVENT) {
            "LOCAL_DOMAIN channel only accepts DOMAIN_EVENT, actual=${entry.messageKind}"
        }
        val event =
            eventSerializer.deserialize(
                entry.payload,
                entry.eventType,
                entry.eventVersion,
            )
        localDomainEventBus.publishEvent(event)
    }
}
