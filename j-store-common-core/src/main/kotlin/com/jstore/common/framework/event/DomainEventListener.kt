package com.jstore.common.framework.event

import org.springframework.core.ResolvableType
import kotlin.reflect.jvm.javaType

interface DomainEventListener<T : DomainEvent> {
    fun supportsAsyncExecution() = false
    fun supportsEventType(eventType: ResolvableType): Boolean {
        val type = this::class.supertypes[0].arguments[0].type ?: return false
        return type.javaType == eventType.type
    }

    fun onDomainEvent(event: DomainEvent)
}