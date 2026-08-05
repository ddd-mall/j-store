package com.jstore.common.framework.messaging

import java.util.concurrent.CopyOnWriteArrayList
import org.springframework.aop.support.AopUtils
import org.springframework.core.ResolvableType

class SpringLocalIntegrationMessageBus(
    handlers: List<IntegrationMessageHandler<*>>,
    private val consumptionRepository: MessageConsumptionRepository,
) : LocalIntegrationMessageBus {
    private data class Registration(
        val messageType: Class<out IntegrationMessage>,
        val handler: IntegrationMessageHandler<*>,
    )

    private val registrations = CopyOnWriteArrayList<Registration>()

    init {
        handlers.forEach(::register)
    }

    override fun publish(message: IntegrationMessage) {
        val matching = registrations.filter { it.messageType.isInstance(message) }
        if (message is IntegrationCommand) {
            check(matching.size == 1) {
                "IntegrationCommand requires exactly one handler: " +
                    "message=${message.messageName}, handlers=${matching.size}"
            }
        }
        matching.forEach { registration ->
            val handler = registration.handler
            if (
                consumptionRepository.tryStart(
                    handler.handlerId(),
                    message.messageId,
                    message.messageName,
                    message.messageVersion,
                )
            ) {
                @Suppress("UNCHECKED_CAST")
                (handler as IntegrationMessageHandler<IntegrationMessage>).handle(message)
            }
        }
    }

    override fun register(handler: IntegrationMessageHandler<*>) {
        require(handler.handlerId().isNotBlank()) {
            "IntegrationMessageHandler ID must not be blank"
        }
        check(registrations.none { it.handler.handlerId() == handler.handlerId() }) {
            "Duplicate IntegrationMessageHandler ID: ${handler.handlerId()}"
        }
        registrations += Registration(resolveMessageType(handler), handler)
    }

    override fun unregister(handler: IntegrationMessageHandler<*>) {
        registrations.removeIf { it.handler == handler }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveMessageType(
        handler: IntegrationMessageHandler<*>
    ): Class<out IntegrationMessage> {
        val handlerClass = AopUtils.getTargetClass(handler)
        val resolved =
            ResolvableType.forClass(handlerClass)
                .`as`(IntegrationMessageHandler::class.java)
                .getGeneric(0)
                .resolve()
        require(resolved != null && IntegrationMessage::class.java.isAssignableFrom(resolved)) {
            "Unable to resolve IntegrationMessageHandler type: ${handlerClass.name}"
        }
        return resolved as Class<out IntegrationMessage>
    }
}
