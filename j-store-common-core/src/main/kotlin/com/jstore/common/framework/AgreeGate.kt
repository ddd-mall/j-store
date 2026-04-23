package com.jstore.common.framework

import com.jstore.common.framework.event.DomainEvent
import java.util.*

interface AgreeGate<I : Identify> : Entity<I> {
    val domainEventQueue: Queue<DomainEvent>

    fun publishEvent(domainEvent: DomainEvent) {
        domainEventQueue.add(domainEvent)
    }

    fun getDomainEvent(): List<DomainEvent> {
        val mutableDomainEvents: MutableList<DomainEvent> = ArrayList()

        while (domainEventQueue.isNotEmpty()) {
            val domainEvent = domainEventQueue.poll()
            mutableDomainEvents.add(domainEvent)
        }
        return mutableDomainEvents.toList()
    }
}