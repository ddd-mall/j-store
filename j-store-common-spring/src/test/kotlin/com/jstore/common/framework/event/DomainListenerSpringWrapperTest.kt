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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.PayloadApplicationEvent

class DomainListenerSpringWrapperTest :
    FunSpec({
        data class StubEvent(
            override val source: Any = "source",
            override val eventId: String = "event-1",
            override val eventName: String = "test.stub-event",
            override val eventVersion: Int = 1,
            override val occurredAt: java.time.Instant =
                java.time.Instant.parse("2025-01-01T00:00:00Z"),
            override val aggregateType: String = "Test",
            override val aggregateId: String = "1",
        ) : ExplicitDomainEvent

        class CountingListener : DomainEventListener<StubEvent> {
            var count = 0

            override fun listenerId(): String = "test.counting-listener"

            override fun onDomainEvent(event: StubEvent) {
                count++
            }
        }

        class RecordingConsumptionRepository(private val accepted: Boolean) :
            DomainEventConsumptionRepository {
            val attempts = mutableListOf<String>()

            override fun tryStart(
                consumerId: String,
                messageId: String,
                messageName: String,
                messageVersion: Int,
            ): Boolean {
                attempts.add("$consumerId:$messageId")
                return accepted
            }
        }

        test("domain listener executes only when idempotency repository accepts the event") {
            val listener = CountingListener()
            val consumptionRepository = RecordingConsumptionRepository(accepted = true)
            val wrapper = DomainListenerSpringWrapper(listener, consumptionRepository)

            wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubEvent()))

            listener.count shouldBe 1
            consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
        }

        test("domain listener is skipped when event was already consumed by the listener") {
            val listener = CountingListener()
            val consumptionRepository = RecordingConsumptionRepository(accepted = false)
            val wrapper = DomainListenerSpringWrapper(listener, consumptionRepository)

            wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubEvent()))

            listener.count shouldBe 0
            consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
        }

        test("domain listener wrapper opts out of async Spring multicaster execution") {
            val wrapper = DomainListenerSpringWrapper(CountingListener())

            wrapper.supportsAsyncExecution() shouldBe false
        }
    })
