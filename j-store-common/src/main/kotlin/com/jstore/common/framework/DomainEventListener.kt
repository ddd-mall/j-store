package com.jstore.common.framework

interface DomainEventListener {
    fun topics(): List<String>
    fun handle(event: DomainEvent)
}