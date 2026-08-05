package com.jstore.common.framework.event

import org.springframework.beans.factory.InitializingBean

class SpringDomainEventListenerRegistrationMachine(
    private val localDomainEventBus: LocalDomainEventBus,
    private val domainEventListeners: List<DomainEventListener<*>>,
) : InitializingBean {
    override fun afterPropertiesSet() {
        domainEventListeners.forEach(localDomainEventBus::register)
    }
}
