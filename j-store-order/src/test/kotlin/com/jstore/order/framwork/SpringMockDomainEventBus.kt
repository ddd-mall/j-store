package com.jstore.order.framwork

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventBus
import com.jstore.common.framework.event.DomainEventListener
import org.springframework.context.ApplicationEvent
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.GenericApplicationListener
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.core.ResolvableType
import java.util.concurrent.Executor

class SpringMockDomainEventBus(executor: Executor? = null) : DomainEventBus {
    private val registry = SimpleApplicationEventMulticaster()

    init {
        executor?.let { registry.setTaskExecutor(it) }
    }

    override fun publishEvent(domainEvent: DomainEvent) {
        registry.multicastEvent(PayloadApplicationEvent(domainEvent.source, domainEvent), ResolvableType.forClass(domainEvent.javaClass))
    }

    override fun register(domainEventListener: DomainEventListener<*>) {
        registry.addApplicationListener(SpringApplicationEventListener(domainEventListener))
    }

    override fun unregister(domainEventListener: DomainEventListener<*>) {
        registry.removeApplicationListener(SpringApplicationEventListener(domainEventListener))
    }



    class SpringApplicationEventListener(
        private val domainEventListener: DomainEventListener<*>,
    ) : GenericApplicationListener {
        override fun onApplicationEvent(event: ApplicationEvent) {
            (event as? PayloadApplicationEvent<*>)?.let {
                (event.payload as? DomainEvent)?.let { domainEvent ->
                    domainEventListener.onDomainEvent(domainEvent)
                }
            }
        }

        override fun supportsEventType(eventType: ResolvableType): Boolean {
            return domainEventListener.supportsEventType(eventType)
        }

        override fun supportsAsyncExecution(): Boolean {
            return domainEventListener.supportsAsyncExecution()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpringApplicationEventListener) return false

            if (domainEventListener != other.domainEventListener) return false

            return true
        }

        override fun hashCode(): Int {
            return domainEventListener.hashCode()
        }


    }
}

