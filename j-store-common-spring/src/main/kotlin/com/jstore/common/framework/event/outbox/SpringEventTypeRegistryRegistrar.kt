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
package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.ExplicitDomainEvent
import java.lang.reflect.Modifier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AnnotationTypeFilter

class SpringEventTypeRegistryRegistrar(
    private val eventTypeRegistry: EventTypeRegistry,
    private val scanPackages: List<String>,
) : SmartInitializingSingleton {
    private val logger = LoggerFactory.getLogger(SpringEventTypeRegistryRegistrar::class.java)

    override fun afterSingletonsInstantiated() {
        val scanner =
            object : ClassPathScanningCandidateComponentProvider(false) {
                override fun isCandidateComponent(
                    beanDefinition:
                        org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
                ): Boolean {
                    return beanDefinition.metadata.isIndependent
                }
            }
        scanner.addIncludeFilter(AnnotationTypeFilter(DomainEventType::class.java))

        var registered = 0
        scanPackages
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .flatMap { scanner.findCandidateComponents(it).asSequence() }
            .forEach { candidate ->
                val eventClass =
                    loadEventClass(
                        candidate.beanClassName
                            ?: throw IllegalStateException(
                                "@DomainEventType candidate has no beanClassName: $candidate"
                            )
                    )
                val eventType =
                    eventClass.getAnnotation(DomainEventType::class.java)
                        ?: throw IllegalStateException(
                            "@DomainEventType candidate is missing runtime annotation: ${eventClass.name}"
                        )
                val eventName = eventType.name
                val eventVersion = eventType.version
                eventTypeRegistry.register(eventName, eventVersion, eventClass)
                registered++
            }

        logger.info(
            "Domain event types registered: count={}, packages={}",
            registered,
            scanPackages,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun loadEventClass(className: String): Class<out DomainEvent> {
        val clazz =
            runCatching { Class.forName(className) }
                .getOrElse { cause ->
                    throw IllegalStateException(
                        "Failed to load @DomainEventType class: $className",
                        cause,
                    )
                }
        require(DomainEvent::class.java.isAssignableFrom(clazz)) {
            "@DomainEventType class must implement DomainEvent: $className"
        }
        require(ExplicitDomainEvent::class.java.isAssignableFrom(clazz)) {
            "@DomainEventType class must implement ExplicitDomainEvent: $className"
        }
        require(!clazz.isInterface && !Modifier.isAbstract(clazz.modifiers)) {
            "@DomainEventType class must be a concrete event class: $className"
        }
        return clazz as Class<out DomainEvent>
    }
}
