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

import com.jstore.common.framework.messaging.MessageConsumptionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.PayloadApplicationEvent

class DomainListenerSpringWrapperTest :
    FunSpec({
        class CountingListener : DomainEventListener<StubDomainEvent> {
            var count = 0

            override fun listenerId(): String = "test.counting-listener"

            override fun onDomainEvent(event: StubDomainEvent) {
                count++
            }
        }

        class RecordingConsumptionRepository(private val accepted: Boolean) :
            MessageConsumptionRepository {
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

            wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubDomainEvent()))

            listener.count shouldBe 1
            consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
        }

        test("domain listener is skipped when event was already consumed by the listener") {
            val listener = CountingListener()
            val consumptionRepository = RecordingConsumptionRepository(accepted = false)
            val wrapper = DomainListenerSpringWrapper(listener, consumptionRepository)

            wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubDomainEvent()))

            listener.count shouldBe 0
            consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
        }

        test("domain listener wrapper opts out of async Spring multicaster execution") {
            val wrapper =
                DomainListenerSpringWrapper(
                    CountingListener(),
                    MessageConsumptionRepository { _, _, _, _ -> true },
                )

            wrapper.supportsAsyncExecution() shouldBe false
        }
    })
