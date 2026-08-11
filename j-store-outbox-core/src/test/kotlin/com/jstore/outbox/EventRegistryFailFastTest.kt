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
package com.jstore.outbox

import com.jstore.common.framework.event.DomainEvent
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import java.time.Instant

class EventRegistryFailFastTest :
    FunSpec({
        test("event type registry rejects duplicate event name and version") {
            val registry = InMemoryEventTypeRegistry()
            registry.register("test.duplicate", 1, FirstDuplicateEvent::class.java)

            shouldThrow<IllegalArgumentException> {
                registry.register("test.duplicate", 1, SecondDuplicateEvent::class.java)
            }
        }

        test("event upcaster registry rejects duplicate event name and source version") {
            shouldThrow<IllegalArgumentException> {
                InMemoryEventUpcasterRegistry(
                    listOf(
                        TestUpcaster("test.upcast", sourceVersion = 1, targetVersion = 2),
                        TestUpcaster("test.upcast", sourceVersion = 1, targetVersion = 3),
                    )
                )
            }
        }
    })

private data class FirstDuplicateEvent(override val eventId: String = "first") : StubDomainEvent

private data class SecondDuplicateEvent(override val eventId: String = "second") : StubDomainEvent

private interface StubDomainEvent : DomainEvent {
    override val eventName: String
        get() = "test.duplicate"

    override val eventVersion: Int
        get() = 1

    override val occurredAt: Instant
        get() = Instant.EPOCH

    override val aggregateType: String
        get() = "Test"

    override val aggregateId: String
        get() = "1"
}

private class TestUpcaster(
    override val eventName: String,
    override val sourceVersion: Int,
    override val targetVersion: Int,
) : EventUpcaster {
    override fun upcast(payload: String): String = payload
}
