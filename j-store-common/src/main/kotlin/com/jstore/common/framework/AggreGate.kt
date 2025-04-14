package com.jstore.common.framework

import com.jstore.common.framework.event.DomainEvent
import java.util.*

interface AggreGate<I : Identify> : Entity<I>  {
    val domainEventQueue: Queue<DomainEvent>
    fun publishEvent(domainEvent: DomainEvent) {
        domainEventQueue.add(domainEvent)
    }
}