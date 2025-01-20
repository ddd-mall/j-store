package com.jstore.common.framework

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.lang.Nullable
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import kotlin.collections.ArrayList

interface DomainEventRegistry {
    fun register(listener: DomainEventListener)
    fun logout(listener: DomainEventListener)
    fun publish(event: DomainEvent)
}


open class SimpleDomainEventRegistry : DomainEventRegistry {
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

    private val executorService: ThreadPoolTaskExecutor?

    @Nullable
    private var domainEventRepository: DomainEventRepository? = null

    private val dispatcher = Thread(::listen)
    private val asyncListenerMap: MutableMap<String, MutableList<DomainEventListener>> = ConcurrentHashMap()
    private val syncEventHandlerMap: MutableMap<String, MutableList<DomainEventListener>> = ConcurrentHashMap()
    private val eventQueue: Queue<DomainEvent> = LinkedBlockingQueue()
    private val mutex = Object()

    init {
        dispatcher.name = "simple-domain-event-registry"
        dispatcher.start()
    }


    constructor(domainEventRepository: DomainEventRepository? = null) {
        this.executorService = ThreadPoolTaskExecutor()
        executorService.setThreadNamePrefix("domain-event-registry-default-")
        executorService.corePoolSize = 1
        executorService.maxPoolSize = 30
        executorService.queueCapacity = 1000
        executorService.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        executorService.initialize()
        this.domainEventRepository = domainEventRepository
    }

    constructor(
        threadPoolTaskExecutor: ThreadPoolTaskExecutor?,
        domainEventRepository: DomainEventRepository? = null
    ) {
        this.executorService = threadPoolTaskExecutor
        this.domainEventRepository = domainEventRepository
    }


    override fun register(listener: DomainEventListener) {
        listener.onTopics().forEach { topic ->
            if (listener.async()) {
                asyncListenerMap[topic]?.add(listener) ?: let {
                    val list: MutableList<DomainEventListener> = ArrayList()
                    asyncListenerMap[topic] = list
                    list.add(listener)
                }
            } else {
                syncEventHandlerMap[topic]?.add(listener) ?: let {
                    val list: MutableList<DomainEventListener> = ArrayList()
                    syncEventHandlerMap[topic] = list
                    list.add(listener)
                }
            }

        }
    }

    override fun logout(listener: DomainEventListener) {
        listener.onTopics().forEach { topic ->
            asyncListenerMap[topic]?.remove(listener)
        }
    }

    override fun publish(event: DomainEvent) {
        domainEventRepository?.save(event)
        syncEventHandlerMap[event.topic()]?.forEach { it.handle(event) }
        asyncInvoke(event)
    }

    private fun asyncInvoke(event: DomainEvent) {
        if (dispatcher.isInterrupted) {
            log.error("dispatcher is interrupted")
            return
        }
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
            asyncListenerMap[event.topic()]?.forEach {
                executorService?.submit { it.handle(event) } ?: it.handle(event)
            }
        }
    }

}