package com.jstore.common.framework

import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ApplicationEventPublisherAware
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

interface DomainEventRegistry {
    fun publishEvent(event: DomainEvent)
}

@Component
class SimpleDomainEventRegistry : DomainEventRegistry, ApplicationEventPublisherAware {
    private var applicationEventPublisher: ApplicationEventPublisher? = null
    private var eventQueue: Queue<DomainEvent>? = LinkedBlockingQueue()

    override fun publishEvent(event: DomainEvent) {
        eventQueue?.add(event)
        applicationEventPublisher?.publishEvent(event)
    }

    override fun setApplicationEventPublisher(applicationEventPublisher: ApplicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher
        val eventQueueTem = eventQueue
        eventQueue = null

        while (true) {
            eventQueueTem?.poll()
                ?.let { applicationEventPublisher.publishEvent(it) }
                ?: break
        }
    }
}