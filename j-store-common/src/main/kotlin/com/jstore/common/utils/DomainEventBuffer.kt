package com.jstore.common.utils

import com.jstore.common.framework.DomainEvent
import java.util.*

class DomainEventBuffer {
    private val eventQueue: Queue<DomainEvent> = LinkedList()
    fun publish(event: DomainEvent) {
        eventQueue.add(event)
    }

    fun hasNext() = eventQueue.isNotEmpty()

    fun getNext() = eventQueue.poll()
}