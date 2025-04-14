package com.jstore.common.framework.event

import org.springframework.context.ApplicationEvent
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.GenericApplicationListener
import org.springframework.core.ResolvableType

class DomainListenerSpringWrapper(
    private val domainEventListener: DomainEventListener,
) : GenericApplicationListener {
    override fun onApplicationEvent(event: ApplicationEvent) {
        (event as? PayloadApplicationEvent<*>)?.let {
            (it.payload as? DomainEvent)?.let { domainEvent ->
                domainEventListener.onDomainEvent(domainEvent)
            }
        }
    }

    override fun supportsAsyncExecution(): Boolean {
        return domainEventListener.supportsAsyncExecution()
    }

    override fun supportsEventType(eventType: ResolvableType): Boolean {
        return domainEventListener.supportsEventType(eventType)
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DomainListenerSpringWrapper) return false

        if (domainEventListener != other.domainEventListener) return false

        return true
    }


    override fun hashCode(): Int {
        return domainEventListener.hashCode()
    }
}