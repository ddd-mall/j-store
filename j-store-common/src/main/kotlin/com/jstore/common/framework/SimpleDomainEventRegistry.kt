package com.jstore.common.framework

import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.HashSet

open class SimpleDomainEventRegistry {
    companion object {
        private var INSTANCE: SimpleDomainEventRegistry? = null
        fun getDefaultInstance(): SimpleDomainEventRegistry {
            INSTANCE?.let { return it }
            synchronized(this) {
                INSTANCE?.let { return it }
                INSTANCE = SimpleDomainEventRegistry(DefaultAsyncExecutorServiceFactory)
                return INSTANCE!!
            }
        }
    }

    private val executorService: ExecutorService

    private constructor(executorServiceFactory: ExecutorServiceFactory) {
        executorService = executorServiceFactory.get()
    }

    private constructor(executorService: ExecutorService) {
        this.executorService = executorService
    }

    private val listenerMap: MutableMap<String, MutableSet<DomainEventListener>> = ConcurrentHashMap()

    private val eventQueue: Queue<DomainEvent> = LinkedBlockingQueue()

    private val mutex = Object()

    fun register(listener: DomainEventListener) {
        listener.topics().forEach { topic ->
            listenerMap[topic]?.add(listener) ?: let {
                val set: MutableSet<DomainEventListener> = HashSet()
                listenerMap[topic] = set
                set.add(listener)
            }

        }
    }

    fun logou(listener: DomainEventListener) {
        listener.topics().forEach { topic ->
            listenerMap[topic]?.remove(listener)
        }
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
            listenerMap[event.topic()]?.forEach { executorService.submit { it.handle(event) } }
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