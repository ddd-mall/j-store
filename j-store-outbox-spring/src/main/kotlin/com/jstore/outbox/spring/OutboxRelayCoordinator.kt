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

import java.time.Clock
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory

/** Receives non-durable hints that committed outbox work may be ready. */
fun interface OutboxRelayTrigger {
    fun requestDrain()
}

interface OutboxRelayExecutionObserver {
    fun recordSuccess()

    fun recordFailure()
}

object NoopOutboxRelayExecutionObserver : OutboxRelayExecutionObserver {
    override fun recordSuccess() = Unit

    override fun recordFailure() = Unit
}

class MonitoringOutboxRelayExecutionObserver(
    private val monitor: OutboxMonitor,
    private val state: SchedulerExecutionState,
    private val clock: Clock = Clock.systemUTC(),
) : OutboxRelayExecutionObserver {
    override fun recordSuccess() {
        clock.instant().also {
            state.recordSuccess(it)
            monitor.recordSchedulerSuccess(it)
        }
    }

    override fun recordFailure() {
        clock.instant().also {
            state.recordFailure(it)
            monitor.recordSchedulerFailure(it)
        }
    }
}

/** Coalesces concurrent wake-up requests into one local relay task. */
class OutboxRelayCoordinator(
    private val publisher: OutboxPublisher,
    private val executor: Executor,
    private val observer: OutboxRelayExecutionObserver = NoopOutboxRelayExecutionObserver,
) : OutboxRelayTrigger {
    private val logger = LoggerFactory.getLogger(OutboxRelayCoordinator::class.java)
    private val pending = AtomicBoolean(false)
    private val running = AtomicBoolean(false)

    override fun requestDrain() {
        pending.set(true)
        scheduleIfNeeded()
    }

    private fun scheduleIfNeeded() {
        if (!running.compareAndSet(false, true)) return
        try {
            executor.execute(::runDrain)
        } catch (failure: RuntimeException) {
            running.set(false)
            logger.warn(
                "Outbox relay wake-up could not be scheduled; periodic polling will retry",
                failure,
            )
        }
    }

    private fun runDrain() {
        var budgetExhausted = false
        try {
            do {
                pending.set(false)
                budgetExhausted = publisher.drainAndPublish().budgetExhausted
                observer.recordSuccess()
            } while (!budgetExhausted && pending.get())
            if (budgetExhausted) pending.set(true)
        } catch (failure: RuntimeException) {
            observer.recordFailure()
            logger.error("Outbox relay drain failed; periodic polling will retry", failure)
        } finally {
            running.set(false)
            if (pending.get()) scheduleIfNeeded()
        }
    }
}
