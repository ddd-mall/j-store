package com.jstore.common.framework

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor

open class SimpleDomainEventRegistry {
    companion object {
        private var INSTANCE: SimpleDomainEventRegistry? = null
        fun defaultInstance(): SimpleDomainEventRegistry {
            INSTANCE?.let { return it }
            synchronized(this) {
                INSTANCE?.let { return it }
                INSTANCE = SimpleDomainEventRegistry()
                return INSTANCE!!
            }
        }

        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    init {
        val thread = Thread {
            listen()
        }
        thread.name = "simple-domain-event-registry"
        thread.start()
    }

    private val executorService: ThreadPoolTaskExecutor

    constructor() {
        executorService = ThreadPoolTaskExecutor()
        executorService.setThreadNamePrefix("domain-event-registry-default-")
        executorService.corePoolSize = 1
        executorService.maxPoolSize = 30
        executorService.queueCapacity = 1000
        executorService.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executorService.initialize()
    }

    constructor(executorServiceFactory: ExecutorServiceFactory) {
        executorService = executorServiceFactory.get()
    }

    constructor(threadPoolTaskExecutor: ThreadPoolTaskExecutor) {
        this.executorService = threadPoolTaskExecutor
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

    fun logout(listener: DomainEventListener) {
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
                    log.error("interrupted occurred", e)
                }
            }
            val event = eventQueue.poll()
            listenerMap[event.topic()]?.forEach { executorService.submit { it.handle(event) } }
        }
    }

}