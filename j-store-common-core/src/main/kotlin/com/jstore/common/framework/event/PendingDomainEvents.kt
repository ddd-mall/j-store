package com.jstore.common.framework.event

import com.jstore.common.framework.RecordsDomainEvents

/** Publishes a stable event snapshot and acknowledges it only after every publication succeeds. */
fun RecordsDomainEvents.publishPendingEvents(publisher: DomainEventPublisher) {
    val pending = pendingDomainEvents()
    pending.forEach(publisher::publishEvent)
    acknowledgeDomainEvents(pending.mapTo(linkedSetOf()) { it.eventId })
}
