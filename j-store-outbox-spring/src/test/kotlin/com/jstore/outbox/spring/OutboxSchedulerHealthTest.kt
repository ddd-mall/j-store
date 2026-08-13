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
package com.jstore.outbox.spring

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class OutboxSchedulerHealthTest :
    FunSpec({
        test("scheduler requests a recovery drain") {
            val trigger = mock<OutboxRelayTrigger>()
            val scheduler = OutboxScheduler(trigger, mock())

            scheduler.schedulePollAndPublish()

            verify(trigger).requestDrain()
        }

        test("scheduler records and rethrows poll failures") {
            val trigger =
                mock<OutboxRelayTrigger> {
                    on { requestDrain() } doThrow IllegalStateException("executor unavailable")
                }
            val scheduler = OutboxScheduler(trigger, mock())

            shouldThrow<IllegalStateException> { scheduler.schedulePollAndPublish() }
        }
    })
