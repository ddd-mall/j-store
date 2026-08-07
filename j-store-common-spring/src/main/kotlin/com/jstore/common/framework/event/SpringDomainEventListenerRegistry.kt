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
package com.jstore.common.framework.event

import org.springframework.context.ConfigurableApplicationContext

class SpringDomainEventListenerRegistry(
    private val applicationContext: ConfigurableApplicationContext,
    private val consumptionRepository: DomainEventConsumptionRepository =
        NoopDomainEventConsumptionRepository,
) : DomainEventListenerRegistry {

    private val registeredListeners: MutableSet<DomainEventListener<*>> = mutableSetOf()

    override fun register(listener: DomainEventListener<*>) {
        DomainEventListenerUtils.requireListeningEventType(listener)
        applicationContext.addApplicationListener(
            DomainListenerSpringWrapper(listener, consumptionRepository)
        )
        registeredListeners.add(listener)
    }

    override fun unregister(listener: DomainEventListener<*>) {
        applicationContext.removeApplicationListener(
            DomainListenerSpringWrapper(
                listener,
                consumptionRepository,
            )
        )
        registeredListeners.remove(listener)
    }

    override fun getListeners(): List<DomainEventListener<*>> {
        return registeredListeners.toList()
    }
}
