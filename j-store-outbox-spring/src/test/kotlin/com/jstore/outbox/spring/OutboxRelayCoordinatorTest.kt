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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OutboxRelayCoordinatorTest :
    FunSpec({
        test("many concurrent signals enqueue only one relay task") {
            val executor = ManualExecutor()
            val publisher = mock<OutboxPublisher>()
            whenever(publisher.drainAndPublish()).thenReturn(OutboxDrainResult(0, false))
            val coordinator = OutboxRelayCoordinator(publisher, executor)

            val callers = Executors.newFixedThreadPool(16)
            try {
                callers.invokeAll(
                    List(100) {
                        java.util.concurrent.Callable {
                            repeat(100) { coordinator.requestDrain() }
                        }
                    }
                )
            } finally {
                callers.shutdown()
            }

            executor.tasks shouldHaveSize 1
            executor.runNext()
            verify(publisher).drainAndPublish()
            executor.tasks shouldHaveSize 0
        }

        test("signal received during drain is handled without overlapping drain") {
            val executor = ManualExecutor()
            val publisher = mock<OutboxPublisher>()
            lateinit var coordinator: OutboxRelayCoordinator
            var active = 0
            var maxActive = 0
            var calls = 0
            whenever(publisher.drainAndPublish()).thenAnswer {
                active++
                maxActive = maxOf(maxActive, active)
                calls++
                if (calls == 1) coordinator.requestDrain()
                active--
                OutboxDrainResult(0, false)
            }
            coordinator = OutboxRelayCoordinator(publisher, executor)

            coordinator.requestDrain()
            executor.runNext()

            calls shouldBe 2
            maxActive shouldBe 1
            executor.tasks shouldHaveSize 0
        }

        test("exhausted drain budget yields before scheduling continuation") {
            val executor = ManualExecutor()
            val publisher = mock<OutboxPublisher>()
            whenever(publisher.drainAndPublish())
                .thenReturn(OutboxDrainResult(2, true), OutboxDrainResult(0, false))
            val coordinator = OutboxRelayCoordinator(publisher, executor)

            coordinator.requestDrain()
            executor.runNext()

            executor.tasks shouldHaveSize 1
            verify(publisher).drainAndPublish()

            executor.runNext()
            verify(publisher, times(2)).drainAndPublish()
            executor.tasks shouldHaveSize 0
        }

        test("executor rejection leaves coordinator recoverable") {
            val publisher = mock<OutboxPublisher>()
            val executor = RejectOnceExecutor()
            whenever(publisher.drainAndPublish()).thenReturn(OutboxDrainResult(0, false))
            val coordinator = OutboxRelayCoordinator(publisher, executor)

            coordinator.requestDrain()
            coordinator.requestDrain()

            executor.acceptedTasks shouldHaveSize 1
            executor.acceptedTasks.single().run()
            verify(publisher).drainAndPublish()
        }

        test("actual drain outcome is reported to operational observer") {
            val executor = ManualExecutor()
            val publisher = mock<OutboxPublisher>()
            val observer = mock<OutboxRelayExecutionObserver>()
            whenever(publisher.drainAndPublish())
                .thenReturn(OutboxDrainResult(0, false))
                .thenThrow(IllegalStateException("database unavailable"))
            val coordinator = OutboxRelayCoordinator(publisher, executor, observer)

            coordinator.requestDrain()
            executor.runNext()
            coordinator.requestDrain()
            executor.runNext()

            verify(observer).recordSuccess()
            verify(observer).recordFailure()
        }

        test("executor rejection is reported as a failed drain") {
            val observer = mock<OutboxRelayExecutionObserver>()
            val coordinator =
                OutboxRelayCoordinator(
                    mock(),
                    Executor { throw RejectedExecutionException() },
                    observer,
                )

            coordinator.requestDrain()

            verify(observer, never()).recordSuccess()
            verify(observer).recordFailure()
        }
    })

private class ManualExecutor : Executor {
    val tasks = ArrayDeque<Runnable>()

    override fun execute(command: Runnable) {
        tasks.addLast(command)
    }

    fun runNext() = tasks.removeFirst().run()
}

private class RejectOnceExecutor : Executor {
    private var rejected = false
    val acceptedTasks = mutableListOf<Runnable>()

    override fun execute(command: Runnable) {
        if (!rejected) {
            rejected = true
            throw RejectedExecutionException("busy")
        }
        acceptedTasks += command
    }
}
