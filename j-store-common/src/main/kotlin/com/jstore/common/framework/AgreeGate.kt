package com.jstore.common.framework

import com.jstore.common.framework.event.DomainEvent
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

interface AgreeGate<I : Identify> : Entity<I> {
    val domainEventQueue: Queue<DomainEvent>
        get() = LinkedBlockingQueue()

    fun publishEvent(domainEvent: DomainEvent) {
        domainEventQueue.add(domainEvent)
    }

    fun getUnpublishedDomainEvent(): List<DomainEvent> {
        val mutableDomainEvents: MutableList<DomainEvent> = ArrayList()

        while (true) {
            val domainEvent = domainEventQueue.poll() ?: break
            mutableDomainEvents.add(domainEvent)
        }
        return mutableDomainEvents.toList()
    }
}