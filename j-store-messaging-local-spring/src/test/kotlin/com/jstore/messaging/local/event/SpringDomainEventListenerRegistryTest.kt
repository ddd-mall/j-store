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
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.springframework.context.support.GenericApplicationContext

class SpringDomainEventListenerRegistryTest :
    FunSpec({
        test("register fails when listener generic event type cannot be resolved") {
            GenericApplicationContext().use { context ->
                val registry =
                    SpringDomainEventListenerRegistry(
                        context,
                        MessageConsumptionRepository { _, _, _, _ -> true },
                    )

                shouldThrow<IllegalArgumentException> {
                    registry.register(UnresolvedGenericListener<DomainEvent>())
                }
            }
        }
    })

private class UnresolvedGenericListener<T : DomainEvent> : DomainEventListener<T> {
    override fun listenerId(): String = "test.unresolved-generic"

    override fun onDomainEvent(event: T) {}
}
