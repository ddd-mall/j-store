package com.jstore.common.framework

import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue

class SimpleDomainEventRegistry {
    private val executorService: ExecutorService
    constructor() {
        executorService =  BizAsyncExecutorServiceFactory.get()
    }
    constructor(executorService: ExecutorService) {
        this.executorService = executorService
    }

    private val listenerList: MutableSet<DomainEventListener> = HashSet()
    private val eventQueue: Queue<DomainEvent> = LinkedBlockingQueue()

    private val mutex = Object()

    fun register(listener: DomainEventListener) {
        listenerList.add(listener)
    }

    fun logou(listener: DomainEventListener) {
        listenerList.remove(listener)
    }

    fun publish(event: DomainEvent) {
        eventQueue.add(event)
        synchronized(mutex) { mutex.notify() }
    }

    private fun listen() {
        while (true) {

            while (eventQueue.isEmpty()) {
                try {
                    synchronized(mutex) { mutex.wait() }
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
            val event = eventQueue.poll()
            listenerList.forEach { executorService.submit { it.handle(event) } }

        }
    }

    init {
        val thread = Thread {
            listen()
        }
        thread.name = "mock-domain-event-registry"
        thread.start()
    }
}

class SimpleDomainEventRegistrySingleToneFactory {
    companion object {
        private val instance = SimpleDomainEventRegistry()
        fun get(): SimpleDomainEventRegistry {
            return instance
        }
    }
}