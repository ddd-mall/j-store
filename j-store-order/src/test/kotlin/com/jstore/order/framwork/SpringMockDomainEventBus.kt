package com.jstore.order.framwork

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventBus
import com.jstore.common.framework.event.DomainEventListener
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.context.event.SimpleApplicationEventMulticaster
import java.util.concurrent.Executor

class SpringMockDomainEventBus(executor: Executor? = null) : DomainEventBus {
    private val registry = SimpleApplicationEventMulticaster()

    init {
        executor?.let { registry.setTaskExecutor(it) }
    }

    override fun publishEvent(domainEvent: DomainEvent) {
        registry.multicastEvent(SpringApplicationDomainEvent(this, domainEvent))
    }

    override fun register(domainEventListener: DomainEventListener) {
        registry.addApplicationListener(SpringApplicationEventListener(domainEventListener))
    }

    override fun unregister(domainEventListener: DomainEventListener) {
        registry.removeApplicationListener(SpringApplicationEventListener(domainEventListener))
    }


    class SpringApplicationDomainEvent(
        source: Any,
        val event: DomainEvent,
    ) : ApplicationEvent(source)


    class SpringApplicationEventListener(
        private val domainListener: DomainEventListener,
    ) : ApplicationListener<SpringApplicationDomainEvent> {
        override fun onApplicationEvent(event: SpringApplicationDomainEvent) {
            domainListener.onDomainEvent(event.event)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SpringApplicationEventListener) return false

            if (domainListener != other.domainListener) return false

            return true
        }

        override fun hashCode(): Int {
            return domainListener.hashCode()
        }
    }
}

