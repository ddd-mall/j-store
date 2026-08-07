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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class OutboxSchedulerHealthTest :
    FunSpec({
        val now = Instant.parse("2026-08-04T02:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)

        test("scheduler records a successful poll") {
            val publisher = mock<OutboxPublisher>()
            val monitor = RecordingOutboxMonitor()
            val state = SchedulerExecutionState()
            val scheduler = OutboxScheduler(publisher, mock(), monitor, clock, state)

            scheduler.schedulePollAndPublish()

            state.snapshot() shouldBe SchedulerExecutionSnapshot(now, null, 0)
            verify(publisher).pollAndPublish()
        }

        test("scheduler records and rethrows poll failures") {
            val publisher =
                mock<OutboxPublisher> {
                    on { pollAndPublish() } doThrow IllegalStateException("database unavailable")
                }
            val monitor = RecordingOutboxMonitor()
            val state = SchedulerExecutionState()
            val scheduler = OutboxScheduler(publisher, mock(), monitor, clock, state)

            shouldThrow<IllegalStateException> { scheduler.schedulePollAndPublish() }

            state.snapshot() shouldBe SchedulerExecutionSnapshot(null, now, 1)
        }
    })

private class RecordingOutboxMonitor : OutboxMonitor {
    override fun recordPoll(delivered: Int, failed: Int) = Unit

    override fun recordDeadLetter(entry: OutboxEntry) = Unit

    override fun recordRequeue(count: Int) = Unit

    override fun recordSchedulerSuccess(at: Instant) = Unit

    override fun recordSchedulerFailure(at: Instant) = Unit
}
