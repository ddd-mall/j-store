package com.jstore.order.framwork

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventListener
import com.jstore.common.framework.DomainEventRegistry
import org.springframework.context.event.SimpleApplicationEventMulticaster
import java.util.concurrent.Executor

class MockDomainEventRegistry(executor: Executor? = null) : DomainEventRegistry {
    private val registry = SimpleApplicationEventMulticaster()
    init {
        executor?.let { registry.setTaskExecutor(it) }
    }
    override fun publishEvent(event: DomainEvent) {
        registry.multicastEvent(event)
    }

    fun register(listener: DomainEventListener) {
        registry.addApplicationListener(listener)
    }
}