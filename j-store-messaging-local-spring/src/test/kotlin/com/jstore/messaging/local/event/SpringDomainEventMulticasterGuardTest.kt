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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import java.util.function.Supplier
import org.springframework.context.event.SimpleApplicationEventMulticaster
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.task.SyncTaskExecutor

class SpringDomainEventMulticasterGuardTest :
    FunSpec({
        test("async multicaster guard only warns by default") {
            GenericApplicationContext().use { context ->
                context.registerBean(
                    AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                    SimpleApplicationEventMulticaster::class.java,
                    Supplier {
                        SimpleApplicationEventMulticaster().apply {
                            setTaskExecutor(SyncTaskExecutor())
                        }
                    },
                )
                context.refresh()

                SpringDomainEventMulticasterGuard(context).afterSingletonsInstantiated()
            }
        }

        test("async multicaster guard fails fast when configured") {
            GenericApplicationContext().use { context ->
                context.registerBean(
                    AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME,
                    SimpleApplicationEventMulticaster::class.java,
                    Supplier {
                        SimpleApplicationEventMulticaster().apply {
                            setTaskExecutor(SyncTaskExecutor())
                        }
                    },
                )
                context.refresh()

                shouldThrow<IllegalStateException> {
                    SpringDomainEventMulticasterGuard(context, failFast = true)
                        .afterSingletonsInstantiated()
                }
            }
        }
    })
