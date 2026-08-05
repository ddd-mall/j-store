package com.jstore.common.framework.messaging

interface IntegrationMessageHandler<T : IntegrationMessage> {
    fun handlerId(): String

    fun handle(message: T)
}

interface LocalIntegrationMessageBus {
    fun publish(message: IntegrationMessage)

    fun register(handler: IntegrationMessageHandler<*>)

    fun unregister(handler: IntegrationMessageHandler<*>)
}
