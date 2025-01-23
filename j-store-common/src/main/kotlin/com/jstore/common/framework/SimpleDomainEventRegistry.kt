package com.jstore.common.framework

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEventPublisher
import org.springframework.lang.Nullable
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ConcurrentHashMap

interface DomainEventRegistry : ApplicationEventPublisher


open class SimpleDomainEventRegistry(
    @Nullable private val executorService: ThreadPoolTaskExecutor?
) : DomainEventRegistry, ApplicationContextAware {
    private var applicationContext: ApplicationContext? = null



    private val asyncListenerMap: MutableMap<String, MutableList<DomainEventListener<*>>> = ConcurrentHashMap()
    private val syncEventHandlerMap: MutableMap<String, MutableList<DomainEventListener<*>>> = ConcurrentHashMap()



    override fun publishEvent(event: Any) {
        applicationContext?.publishEvent(event)
    }

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }


}