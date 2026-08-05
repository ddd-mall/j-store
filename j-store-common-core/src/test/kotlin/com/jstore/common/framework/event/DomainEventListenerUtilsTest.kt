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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class DomainEventListenerUtilsTest :
    FunSpec({
        test("resolves direct listener generic event type") {
            DomainEventListenerUtils.getListeningEventType(DirectTestEventListener()) shouldBe
                TestEvent::class.java
        }

        test("resolves listener event type through parameterized base class") {
            DomainEventListenerUtils.getListeningEventType(InheritedTestEventListener()) shouldBe
                TestEvent::class.java
        }

        test("fails when listener event type is still an unresolved type variable") {
            shouldThrow<IllegalArgumentException> {
                DomainEventListenerUtils.requireListeningEventType(
                    RawGenericTestEventListener<DomainEvent>()
                )
            }
        }
    })

private data class TestEvent(
    override val eventId: String = "event-1",
    override val eventName: String = "test.event",
    override val eventVersion: Int = 1,
    override val occurredAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
    override val aggregateType: String = "Test",
    override val aggregateId: String = "1",
) : DomainEvent

private class DirectTestEventListener : DomainEventListener<TestEvent> {
    override fun listenerId(): String = "test.direct"

    override fun onDomainEvent(event: TestEvent) {}
}

private abstract class BaseTestEventListener<T : DomainEvent> : DomainEventListener<T> {
    override fun listenerId(): String = "test.base"
}

private class InheritedTestEventListener : BaseTestEventListener<TestEvent>() {
    override fun onDomainEvent(event: TestEvent) {}
}

private class RawGenericTestEventListener<T : DomainEvent> : DomainEventListener<T> {
    override fun listenerId(): String = "test.raw-generic"

    override fun onDomainEvent(event: T) {}
}
