package com.jstore.order.config

import com.jstore.common.framework.DomainEventRegistry
import com.jstore.common.framework.SimpleDomainEventRegistry


object TestBeanConfig {

    private var domainEventRegistry: SimpleDomainEventRegistry? = null
    private val mutex: Any = Object()

    fun getSimpleDomainEventRegistry(): DomainEventRegistry {
        domainEventRegistry?.let { return it }
        synchronized(mutex) {
            domainEventRegistry?.let { return it }
            domainEventRegistry = SimpleDomainEventRegistry.defaultInstance()
            return domainEventRegistry!!
        }
    }
}