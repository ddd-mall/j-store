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

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.ApplicationContext
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.context.support.AbstractApplicationContext

class SpringDomainEventMulticasterGuard(
    private val applicationContext: ApplicationContext,
    private val failFast: Boolean = false,
) : SmartInitializingSingleton {
    private val logger = LoggerFactory.getLogger(SpringDomainEventMulticasterGuard::class.java)

    override fun afterSingletonsInstantiated() {
        val multicaster =
            runCatching {
                applicationContext.getBean(
                    AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME
                )
            }
                .getOrNull() ?: return

        if (multicaster is SimpleApplicationEventMulticaster && multicaster.hasTaskExecutor()) {
            val message =
                "Spring applicationEventMulticaster is configured with an async taskExecutor. " +
                    "DomainEventListener wrappers opt out of async execution to preserve reliable outbox relay transactions."
            if (failFast) {
                throw IllegalStateException(message)
            }
            logger.warn(message)
        }
    }

    private fun SimpleApplicationEventMulticaster.hasTaskExecutor(): Boolean {
        return runCatching {
                val field =
                    SimpleApplicationEventMulticaster::class.java.getDeclaredField("taskExecutor")
                field.isAccessible = true
                field.get(this) != null
            }
            .getOrDefault(false)
    }
}
