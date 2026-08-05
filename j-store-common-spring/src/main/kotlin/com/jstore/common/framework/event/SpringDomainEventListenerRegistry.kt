package com.jstore.common.framework.event

import com.jstore.common.framework.messaging.MessageConsumptionRepository
import org.springframework.context.ConfigurableApplicationContext

class SpringDomainEventListenerRegistry(
    private val applicationContext: ConfigurableApplicationContext,
    private val consumptionRepository: MessageConsumptionRepository,
) : DomainEventListenerRegistry {

    private val registeredListeners: MutableSet<DomainEventListener<*>> = mutableSetOf()

    override fun register(listener: DomainEventListener<*>) {
        SpringDomainEventListenerTypeResolver.require(listener)
        applicationContext.addApplicationListener(
            DomainListenerSpringWrapper(listener, consumptionRepository)
        )
        registeredListeners.add(listener)
    }

    override fun unregister(listener: DomainEventListener<*>) {
        applicationContext.removeApplicationListener(
            DomainListenerSpringWrapper(
                listener,
                consumptionRepository,
            )
        )
        registeredListeners.remove(listener)
    }

    override fun getListeners(): List<DomainEventListener<*>> {
        return registeredListeners.toList()
    }
}
