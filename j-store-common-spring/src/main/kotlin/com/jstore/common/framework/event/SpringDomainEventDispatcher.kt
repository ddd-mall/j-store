package com.jstore.common.framework.event

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.stereotype.Component

@Component
class SpringDomainEventDispatcher : DomainEventDispatcher, ApplicationContextAware {
    private lateinit var applicationContext: AbstractApplicationContext

    override fun dispatch(domainEvent: DomainEvent, listeners: Iterable<DomainEventListener>) {
        applicationContext.applicationListeners
        applicationContext.publishEvent(DomainEventSpringWrapper(domainEvent))
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        (applicationContext as? AbstractApplicationContext)?.let {
            this.applicationContext = applicationContext
        }
    }
}