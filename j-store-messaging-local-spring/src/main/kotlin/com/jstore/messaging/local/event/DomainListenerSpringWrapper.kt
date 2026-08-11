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
package com.jstore.messaging.local.event

import com.jstore.common.framework.event.*
import com.jstore.messaging.MessageConsumptionRepository
import com.jstore.messaging.tryStart
import org.springframework.context.ApplicationEvent
import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.GenericApplicationListener
import org.springframework.core.ResolvableType

class DomainListenerSpringWrapper(
    private val domainEventListener: DomainEventListener<*>,
    private val consumptionRepository: MessageConsumptionRepository,
) : GenericApplicationListener {
    private val listenerEventType =
        SpringDomainEventListenerTypeResolver.require(domainEventListener)

    override fun onApplicationEvent(event: ApplicationEvent) {
        (event as? PayloadApplicationEvent<*>)?.let {
            (it.payload as? DomainEvent)?.let { domainEvent ->
                if (listenerEventType.isInstance(domainEvent)) {
                    val listenerId = domainEventListener.listenerId()
                    if (consumptionRepository.tryStart(listenerId, domainEvent)) {
                        @Suppress("UNCHECKED_CAST")
                        (domainEventListener as DomainEventListener<DomainEvent>).onDomainEvent(
                            domainEvent
                        )
                    }
                }
            }
        }
    }

    override fun supportsAsyncExecution(): Boolean {
        return false
    }

    override fun supportsEventType(eventType: ResolvableType): Boolean {
        val isPayloadApplicationEvent = eventType.rawClass == PayloadApplicationEvent::class.java
        if (!isPayloadApplicationEvent) {
            return false
        }

        val payloadType = eventType.generics.firstOrNull()?.resolve() ?: return false
        return listenerEventType.isAssignableFrom(payloadType)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DomainListenerSpringWrapper) return false

        if (domainEventListener != other.domainEventListener) return false

        return true
    }

    override fun hashCode(): Int {
        return domainEventListener.hashCode()
    }
}
