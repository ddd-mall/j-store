package com.jstore.common.framework.event

import org.springframework.context.ConfigurableApplicationContext


class SpringDomainEventListenerRegistry(
    private val applicationContext: ConfigurableApplicationContext,
    private val consumptionRepository: DomainEventConsumptionRepository = NoopDomainEventConsumptionRepository,
) :
    DomainEventListenerRegistry {


    private val registeredListeners: MutableSet<DomainEventListener<*>> = mutableSetOf()

    override fun register(listener: DomainEventListener<*>) {
        DomainEventListenerUtils.requireListeningEventType(listener)
        applicationContext.addApplicationListener(DomainListenerSpringWrapper(listener, consumptionRepository))
        registeredListeners.add(listener)
    }

    override fun unregister(listener: DomainEventListener<*>) {
        applicationContext.removeApplicationListener(
            DomainListenerSpringWrapper(
                listener,
                consumptionRepository
            )
        )
        registeredListeners.remove(listener)
    }

    override fun getListeners(): List<DomainEventListener<*>> {
        return registeredListeners.toList()
    }


}
