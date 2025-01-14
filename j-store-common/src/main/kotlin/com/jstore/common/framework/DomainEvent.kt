package com.jstore.common.framework

import com.jstore.common.properties.Id

interface DomainEvent : Entity<DomainEventId> {
    fun topic() : String
    fun markSuccess(listener: DomainEventListener)
    fun successesOn(): List<String>
    val mutex: Object
}

abstract class DomainEventBase : DomainEvent {
    @Transient override val mutex = Object()
    private val successes = mutableListOf<String>()
    override fun markSuccess(listener: DomainEventListener) {
        synchronized(successes) {
            successes.add(listener.name())
        }
    }
    override fun successesOn(): List<String> = successes
}

class  DomainEventId(override val value: Long): Id<Long>(value)