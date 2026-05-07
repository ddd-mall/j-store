package com.jstore.common.framework.event

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.ApplicationContext
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.context.event.SimpleApplicationEventMulticaster

class SpringDomainEventMulticasterGuard(
    private val applicationContext: ApplicationContext,
    private val failFast: Boolean = false,
) : SmartInitializingSingleton {
    private val logger = LoggerFactory.getLogger(SpringDomainEventMulticasterGuard::class.java)

    override fun afterSingletonsInstantiated() {
        val multicaster = runCatching {
            applicationContext.getBean(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME)
        }.getOrNull() ?: return

        if (multicaster is SimpleApplicationEventMulticaster && multicaster.hasTaskExecutor()) {
            val message = "Spring applicationEventMulticaster is configured with an async taskExecutor. " +
                "DomainEventListener wrappers opt out of async execution to preserve reliable outbox relay transactions."
            if (failFast) {
                throw IllegalStateException(message)
            }
            logger.warn(message)
        }
    }

    private fun SimpleApplicationEventMulticaster.hasTaskExecutor(): Boolean {
        return runCatching {
            val field = SimpleApplicationEventMulticaster::class.java.getDeclaredField("taskExecutor")
            field.isAccessible = true
            field.get(this) != null
        }.getOrDefault(false)
    }
}
