package com.jstore.common.framework.event

import org.springframework.context.ApplicationEvent
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.GenericApplicationListener
import org.springframework.core.ResolvableType

class DomainListenerSpringWrapper(
    private val domainEventListener: DomainEventListener<*>,
) : GenericApplicationListener {
    override fun onApplicationEvent(event: ApplicationEvent) {
        (event as? PayloadApplicationEvent<*>)?.let {
            (it.payload as? DomainEvent)?.let { domainEvent ->
                if (DomainEventListenerUtils.supportsEvent(domainEventListener, domainEvent)) {
                    @Suppress("UNCHECKED_CAST")
                    (domainEventListener as DomainEventListener<DomainEvent>).onDomainEvent(domainEvent)
                }
            }
        }
    }

    override fun supportsAsyncExecution(): Boolean {
        return domainEventListener.supportsAsyncExecution()
    }

    override fun supportsEventType(eventType: ResolvableType): Boolean {
        val isPayloadApplicationEvent = eventType.rawClass == PayloadApplicationEvent::class.java
        if (!isPayloadApplicationEvent) {
            return false
        }

        val payloadType = eventType.generics.firstOrNull()?.resolve() ?: return false
        val listenerEventType = DomainEventListenerUtils.getListeningEventType(domainEventListener) ?: return false
        return listenerEventType.isAssignableFrom(payloadType)
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