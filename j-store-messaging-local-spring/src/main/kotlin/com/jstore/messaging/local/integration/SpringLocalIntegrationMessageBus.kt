/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.messaging.local.integration

import com.jstore.messaging.*
import java.util.concurrent.CopyOnWriteArrayList
import org.slf4j.MDC
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
        publishInternal(message, null)
    }

    override fun publish(message: IntegrationMessage, deliveryOrder: MessageDeliveryOrder) {
        publishInternal(message, deliveryOrder)
    }

    private fun publishInternal(message: IntegrationMessage, deliveryOrder: MessageDeliveryOrder?) {
        withMessageLoggingContext(message) {
            val matching = registrations.filter { it.messageType.isInstance(message) }
            if (message is IntegrationCommand) {
                check(matching.size == 1) {
                    "IntegrationCommand requires exactly one handler: " +
                        "message=${message.messageName}, handlers=${matching.size}"
                }
            }
            if (
                deliveryOrder != null &&
                    !consumptionRepository.tryStartOrdered(
                        BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS,
                        message.messageId,
                        message.messageName,
                        message.messageVersion,
                        deliveryOrder,
                    )
            ) {
                return@withMessageLoggingContext
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
    }

    private fun withMessageLoggingContext(message: IntegrationMessage, action: () -> Unit) {
        val values =
            mapOf(
                "message_id" to message.messageId,
                "correlation_id" to message.correlationId,
                "causation_id" to message.causationId,
                "transport_id" to "local",
            )
        val previous = values.keys.associateWith(MDC::get)
        try {
            values.forEach { (key, value) ->
                if (value == null) MDC.remove(key) else MDC.put(key, value)
            }
            action()
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) MDC.remove(key) else MDC.put(key, value)
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
