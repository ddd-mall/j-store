package com.jstore.common.framework.event

import com.jstore.common.framework.AgreeGate

/** Publishes a stable event snapshot and acknowledges it only after every publication succeeds. */
fun AgreeGate<*>.publishPendingEvents(publisher: DomainEventPublisher) {
    val pending = domainEventQueue.toList()
    pending.forEach(publisher::publishEvent)
    repeat(pending.size) { domainEventQueue.poll() }
}
