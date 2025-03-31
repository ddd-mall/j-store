package com.jstore.common.framework.event

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.stereotype.Component

@Component
class SpringDomainEventListenerRegistry : DomainEventListenerRegistry, ApplicationContextAware {

    private lateinit var applicationContext: AbstractApplicationContext
    private val registeredListeners: MutableSet<DomainEventListener> = mutableSetOf()

    override fun register(listener: DomainEventListener) {
        applicationContext.addApplicationListener(DomainListenerSpringWrapper(listener))
        registeredListeners.add(listener)
    }

    override fun unregister(listener: DomainEventListener) {
        applicationContext.removeApplicationListener(
            DomainListenerSpringWrapper(
                listener
            )
        )
        registeredListeners.remove(listener)
    }

    override fun getListeners(): List<DomainEventListener> {
        return registeredListeners.toList()
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        (applicationContext as? AbstractApplicationContext)?.let { this.applicationContext = applicationContext }
    }




}