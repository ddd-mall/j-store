package com.jstore.common.framework

interface DomainEventListener {
    fun name(): String
    fun onTopics(): List<String>
    fun async(): Boolean
    fun handle(event: DomainEvent)
    fun invoke(event: DomainEvent) {
        handle(event)
        event.markSuccess(this)
        event.mutex.notifyAll()
    }
}