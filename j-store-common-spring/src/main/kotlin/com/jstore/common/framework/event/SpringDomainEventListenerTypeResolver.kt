package com.jstore.common.framework.event

import org.springframework.aop.support.AopUtils

/** Keeps Spring proxy awareness out of the framework-free common-core module. */
object SpringDomainEventListenerTypeResolver {
    fun require(listener: DomainEventListener<*>): Class<out DomainEvent> =
        DomainEventListenerUtils.requireListeningEventType(AopUtils.getTargetClass(listener))
}
