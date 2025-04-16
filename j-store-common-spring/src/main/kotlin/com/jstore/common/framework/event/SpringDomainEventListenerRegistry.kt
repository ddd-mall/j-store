package com.jstore.common.framework.event

import org.springframework.context.ConfigurableApplicationContext


class SpringDomainEventListenerRegistry(private val applicationContext: ConfigurableApplicationContext) :
    DomainEventListenerRegistry {


    private val registeredListeners: MutableSet<DomainEventListener<*>> = mutableSetOf()

    override fun register(listener: DomainEventListener<*>) {
        applicationContext.addApplicationListener(DomainListenerSpringWrapper(listener))
        registeredListeners.add(listener)
    }

    override fun unregister(listener: DomainEventListener<*>) {
        applicationContext.removeApplicationListener(
            DomainListenerSpringWrapper(
                listener
            )
        )
        registeredListeners.remove(listener)
    }

    override fun getListeners(): List<DomainEventListener<*>> {
        return registeredListeners.toList()
    }


}