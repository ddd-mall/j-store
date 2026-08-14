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

import com.jstore.outbox.*
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.time.Instant

interface OutboxMonitor {
    fun recordPoll(delivered: Int, failed: Int)

    fun recordDelivery(entry: OutboxEntry, successful: Boolean) {}

    fun recordDeadLetter(entry: OutboxEntry)

    fun recordRequeue(count: Int)

    fun recordSchedulerSuccess(at: Instant) {}

    fun recordSchedulerFailure(at: Instant) {}
}

object NoopOutboxMonitor : OutboxMonitor {
    override fun recordPoll(delivered: Int, failed: Int) {}

    override fun recordDelivery(entry: OutboxEntry, successful: Boolean) {}

    override fun recordDeadLetter(entry: OutboxEntry) {}

    override fun recordRequeue(count: Int) {}

    override fun recordSchedulerSuccess(at: Instant) {}

    override fun recordSchedulerFailure(at: Instant) {}
}

class MicrometerOutboxMonitor(
    private val meterRegistry: MeterRegistry,
    private val outboxEntryRepository: OutboxEntryRepository,
    private val operationalHealth: OutboxOperationalHealth,
    private val schedulerState: SchedulerExecutionState,
    configuredTransportIds: Set<String> = emptySet(),
    private val schedulerFailureThreshold: Int = 3,
) : OutboxMonitor {

    init {
        require(schedulerFailureThreshold > 0) {
            "schedulerFailureThreshold must be greater than 0"
        }
        val transportIds =
            (configuredTransportIds + operationalHealth.snapshot().transports.keys).sorted()
        OutboxEntryStatus.entries.forEach { status ->
            meterRegistry.gauge(
                "jstore.outbox.entries",
                listOf(Tag.of("status", status.name), Tag.of("transportId", "all")),
                status,
            ) {
                outboxEntryRepository.countByStatus(it).toDouble()
            }
            transportIds.forEach { transportId ->
                meterRegistry.gauge(
                    "jstore.outbox.entries",
                    listOf(
                        Tag.of("status", status.name),
                        Tag.of("transportId", transportId),
                    ),
                    status,
                ) {
                    outboxEntryRepository.countByStatus(it, transportId).toDouble()
                }
            }
        }

        meterRegistry.gauge(
            "jstore.outbox.oldest_ready.lag",
            listOf(Tag.of("transportId", "all")),
            operationalHealth,
        ) {
            it.snapshot().oldestReadyLag.toMillis() / 1000.0
        }

        meterRegistry.gauge(
            "jstore.outbox.expired_locks",
            listOf(Tag.of("transportId", "all")),
            operationalHealth,
        ) {
            it.snapshot().expiredLockCount.toDouble()
        }

        registerAlertGauge("lag", "all") { it.lagAlert }
        registerAlertGauge("expired_lock", "all") { it.expiredLockAlert }
        registerAlertGauge("dead_letter", "all") { it.deadLetterAlert }
        meterRegistry.gauge(
            "jstore.outbox.alert",
            listOf(Tag.of("reason", "scheduler_failure"), Tag.of("transportId", "all")),
            schedulerState,
        ) {
            if (it.snapshot().consecutiveFailures >= schedulerFailureThreshold) 1.0 else 0.0
        }
        transportIds.forEach { transportId ->
            meterRegistry.gauge(
                "jstore.outbox.oldest_ready.lag",
                listOf(Tag.of("transportId", transportId)),
                operationalHealth,
            ) {
                it.snapshot().transports[transportId]?.oldestReadyLag?.toMillis()?.div(1000.0)
                    ?: 0.0
            }
            meterRegistry.gauge(
                "jstore.outbox.expired_locks",
                listOf(Tag.of("transportId", transportId)),
                operationalHealth,
            ) {
                it.snapshot().transports[transportId]?.expiredLockCount?.toDouble() ?: 0.0
            }
            registerTransportAlertGauge("lag", transportId) { it.lagAlert }
            registerTransportAlertGauge("expired_lock", transportId) { it.expiredLockAlert }
            registerTransportAlertGauge("dead_letter", transportId) { it.deadLetterAlert }
        }

        meterRegistry.gauge(
            "jstore.outbox.scheduler.last_success",
            schedulerState,
        ) {
            it.snapshot().lastSuccessAt?.epochSecond?.toDouble() ?: 0.0
        }

        meterRegistry.gauge(
            "jstore.outbox.scheduler.last_failure",
            schedulerState,
        ) {
            it.snapshot().lastFailureAt?.epochSecond?.toDouble() ?: 0.0
        }

        meterRegistry.gauge(
            "jstore.outbox.scheduler.consecutive_failures",
            schedulerState,
        ) {
            it.snapshot().consecutiveFailures.toDouble()
        }
    }

    override fun recordPoll(delivered: Int, failed: Int) {
        meterRegistry.counter("jstore.outbox.delivered").increment(delivered.toDouble())
        meterRegistry.counter("jstore.outbox.failed").increment(failed.toDouble())
    }

    override fun recordDelivery(entry: OutboxEntry, successful: Boolean) {
        meterRegistry
            .counter(
                "jstore.outbox.delivery",
                "transportId",
                entry.transportId,
                "outcome",
                if (successful) "published" else "failed",
            )
            .increment()
    }

    override fun recordDeadLetter(entry: OutboxEntry) {
        meterRegistry
            .counter(
                "jstore.outbox.dead_letter",
                "eventType",
                entry.eventType,
                "aggregateType",
                entry.aggregateType,
                "transportId",
                entry.transportId,
            )
            .increment()
    }

    override fun recordRequeue(count: Int) {
        meterRegistry.counter("jstore.outbox.dead_letter.requeued").increment(count.toDouble())
    }

    override fun recordSchedulerSuccess(at: Instant) {
        meterRegistry.counter("jstore.outbox.scheduler.success").increment()
    }

    override fun recordSchedulerFailure(at: Instant) {
        meterRegistry.counter("jstore.outbox.scheduler.failure").increment()
    }

    private fun registerAlertGauge(
        reason: String,
        transportId: String,
        active: (OutboxOperationalSnapshot) -> Boolean,
    ) {
        meterRegistry.gauge(
            "jstore.outbox.alert",
            listOf(Tag.of("reason", reason), Tag.of("transportId", transportId)),
            operationalHealth,
        ) {
            if (active(it.snapshot())) 1.0 else 0.0
        }
    }

    private fun registerTransportAlertGauge(
        reason: String,
        transportId: String,
        active: (OutboxTransportOperationalSnapshot) -> Boolean,
    ) {
        meterRegistry.gauge(
            "jstore.outbox.alert",
            listOf(Tag.of("reason", reason), Tag.of("transportId", transportId)),
            operationalHealth,
        ) {
            val snapshot = it.snapshot().transports[transportId]
            if (snapshot != null && active(snapshot)) 1.0 else 0.0
        }
    }
}
