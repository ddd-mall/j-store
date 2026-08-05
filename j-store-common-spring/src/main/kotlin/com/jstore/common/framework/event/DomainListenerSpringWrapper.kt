package com.jstore.common.framework.event

import com.jstore.common.framework.messaging.MessageConsumptionRepository
import com.jstore.common.framework.messaging.tryStart
import org.springframework.context.ApplicationEvent
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.GenericApplicationListener
import org.springframework.core.ResolvableType

class DomainListenerSpringWrapper(
    private val domainEventListener: DomainEventListener<*>,
    private val consumptionRepository: MessageConsumptionRepository,
) : GenericApplicationListener {
    private val listenerEventType = SpringDomainEventListenerTypeResolver.require(domainEventListener)

    override fun onApplicationEvent(event: ApplicationEvent) {
        (event as? PayloadApplicationEvent<*>)?.let {
            (it.payload as? DomainEvent)?.let { domainEvent ->
                if (listenerEventType.isInstance(domainEvent)) {
                    val listenerId = domainEventListener.listenerId()
                    if (consumptionRepository.tryStart(listenerId, domainEvent)) {
                        @Suppress("UNCHECKED_CAST")
                        (domainEventListener as DomainEventListener<DomainEvent>).onDomainEvent(
                            domainEvent
                        )
                    }
                }
            }
        }
    }

    override fun supportsAsyncExecution(): Boolean {
        return false
    }

    override fun supportsEventType(eventType: ResolvableType): Boolean {
        val isPayloadApplicationEvent = eventType.rawClass == PayloadApplicationEvent::class.java
        if (!isPayloadApplicationEvent) {
            return false
        }

        val payloadType = eventType.generics.firstOrNull()?.resolve() ?: return false
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
