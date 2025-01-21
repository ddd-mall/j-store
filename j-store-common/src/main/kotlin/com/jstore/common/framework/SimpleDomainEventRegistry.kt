package com.jstore.common.framework

import org.springframework.lang.Nullable
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException

interface DomainEventRegistry {
    fun register(listener: DomainEventListener)
    fun logout(listener: DomainEventListener)
    fun publish(event: DomainEvent)
}


open class SimpleDomainEventRegistry(
    threadPoolTaskExecutor: ThreadPoolTaskExecutor?, @Nullable
    private var domainEventRepository: DomainEventRepository? = null
) : DomainEventRegistry {

    private val executorService: ThreadPoolTaskExecutor? = threadPoolTaskExecutor
    private val asyncListenerMap: MutableMap<String, MutableList<DomainEventListener>> = ConcurrentHashMap()
    private val syncEventHandlerMap: MutableMap<String, MutableList<DomainEventListener>> = ConcurrentHashMap()


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

        asyncListenerMap[event.topic()]?.forEach {
            try {
                executorService?.submit { it.handle(event) } ?: it.handle(event)
            } catch (e: RejectedExecutionException) {
                it.handle(event)
            }
        }
    }

}