package com.jstore.com.jstore.order.framework

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class SpringDomainEventPublisher : DomainEventPublisher, ApplicationContextAware {
    private lateinit var applicationContext: ApplicationContext
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    override fun <T : DomainEvent> publishEvent(event: T) {

    }




}