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

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.stableDomainEventId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

@DomainEventType(name = "test.scanned-event", version = 7)
data class ScannedEvent(
    override val source: Any = "source",
    override val occurredAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
) : ExplicitDomainEvent {
    override val eventName: String = "test.scanned-event"
    override val eventVersion: Int = 7
    override val aggregateType: String = "Test"
    override val aggregateId: String = "scanned"
    override val eventId: String =
        stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}

class SpringEventTypeRegistryRegistrarTest :
    FunSpec({
        test("registrar scans domain event types and registers stable annotated name") {
            val registry = InMemoryEventTypeRegistry()
            val registrar =
                SpringEventTypeRegistryRegistrar(
                    registry,
                    listOf("com.jstore.common.framework.event.outbox"),
                )

            registrar.afterSingletonsInstantiated()

            registry.resolve("test.scanned-event", 7) shouldBe ScannedEvent::class.java
        }

        test("registrar fails fast when annotated class is not a domain event") {
            val registrar =
                SpringEventTypeRegistryRegistrar(
                    InMemoryEventTypeRegistry(),
                    listOf("org.example.jstore.invalidtype"),
                )

            shouldThrow<IllegalArgumentException> {
                registrar.afterSingletonsInstantiated()
            }
        }
    })
