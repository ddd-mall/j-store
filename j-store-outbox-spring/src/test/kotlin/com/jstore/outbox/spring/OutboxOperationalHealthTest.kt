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
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class OutboxOperationalHealthTest :
    FunSpec({
        val now = Instant.parse("2026-08-04T02:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)

        test("health distinguishes never-run healthy degraded and failed") {
            val repository = mock<OutboxEntryRepository>()
            whenever(repository.findOldestReadyAt(now, 5)).thenReturn(null)
            whenever(repository.countExpiredLocks(now)).thenReturn(0)
            whenever(repository.countByStatus(OutboxEntryStatus.DEAD_LETTER)).thenReturn(0)
            val state = SchedulerExecutionState()
            val health =
                OutboxOperationalHealth(
                    repository,
                    state,
                    OutboxObservabilityProperties(),
                    5,
                    clock,
                )

            health.snapshot().status shouldBe OutboxOperationalStatus.NOT_RUN
            state.recordSuccess(now)
            health.snapshot().status shouldBe OutboxOperationalStatus.HEALTHY

            whenever(repository.findOldestReadyAt(now, 5)).thenReturn(now.minusSeconds(301))
            health.snapshot().status shouldBe OutboxOperationalStatus.DEGRADED

            repeat(3) { state.recordFailure(now) }
            health.snapshot().status shouldBe OutboxOperationalStatus.FAILED
        }

        test("micrometer exposes lag expired-lock scheduler and alert gauges") {
            val repository = mock<OutboxEntryRepository>()
            whenever(repository.findOldestReadyAt(now, 5)).thenReturn(now.minusSeconds(120))
            whenever(repository.countExpiredLocks(now)).thenReturn(2)
            whenever(repository.countByStatus(OutboxEntryStatus.DEAD_LETTER)).thenReturn(4)
            OutboxEntryStatus.entries.forEach {
                whenever(repository.countByStatus(it))
                    .thenReturn(if (it == OutboxEntryStatus.DEAD_LETTER) 4 else 0)
            }
            val registry = SimpleMeterRegistry()
            val state = SchedulerExecutionState().apply { recordSuccess(now.minusSeconds(10)) }
            val health =
                OutboxOperationalHealth(
                    repository,
                    state,
                    OutboxObservabilityProperties(Duration.ofSeconds(60), 1, 3, 3),
                    5,
                    clock,
                )

            val monitor = MicrometerOutboxMonitor(registry, repository, health, state)
            val kafkaEntry =
                OutboxEntry(
                    id = "entry-1",
                    eventType = "inventory.reserve",
                    payload = "{}",
                    aggregateType = "Order",
                    aggregateId = "42",
                    status = OutboxEntryStatus.PENDING,
                    createdAt = now,
                    updatedAt = now,
                    messageKind = OutboxMessageKind.INTEGRATION_COMMAND,
                    deliveryTarget = OutboxDeliveryTarget.BROKER,
                    transportId = "kafka",
                    orderingKey = OutboxOrderingKeys.integration("inventory.reserve", "42"),
                    sequenceNo = 1,
                )
            monitor.recordDelivery(kafkaEntry, true)

            registry
                .get("jstore.outbox.oldest_ready.lag")
                .tag("transportId", "all")
                .gauge()
                .value()
                .shouldBeExactly(120.0)
            registry
                .get("jstore.outbox.expired_locks")
                .tag("transportId", "all")
                .gauge()
                .value()
                .shouldBeExactly(2.0)
            registry
                .get("jstore.outbox.alert")
                .tag("reason", "lag")
                .tag("transportId", "all")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
            registry
                .get("jstore.outbox.alert")
                .tag("reason", "expired_lock")
                .tag("transportId", "all")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
            registry
                .get("jstore.outbox.alert")
                .tag("reason", "dead_letter")
                .tag("transportId", "all")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
            registry
                .get("jstore.outbox.scheduler.last_success")
                .gauge()
                .value()
                .shouldBeExactly(now.minusSeconds(10).epochSecond.toDouble())
            registry
                .get("jstore.outbox.delivery")
                .tag("transportId", "kafka")
                .tag("outcome", "published")
                .counter()
                .count()
                .shouldBeExactly(1.0)
            repeat(3) { state.recordFailure(now) }
            registry
                .get("jstore.outbox.alert")
                .tag("reason", "scheduler_failure")
                .tag("transportId", "all")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
        }

        test("health and gauges isolate operational state by transport") {
            val repository = mock<OutboxEntryRepository>()
            whenever(repository.findTransportIds()).thenReturn(setOf("local", "kafka"))
            whenever(repository.findOldestReadyAt(now, 5, "local")).thenReturn(now.minusSeconds(30))
            whenever(repository.findOldestReadyAt(now, 5, "kafka"))
                .thenReturn(now.minusSeconds(301))
            whenever(repository.countExpiredLocks(now, "local")).thenReturn(0)
            whenever(repository.countExpiredLocks(now, "kafka")).thenReturn(2)
            whenever(repository.countByStatus(OutboxEntryStatus.DEAD_LETTER, "local")).thenReturn(0)
            whenever(repository.countByStatus(OutboxEntryStatus.DEAD_LETTER, "kafka")).thenReturn(4)
            OutboxEntryStatus.entries.forEach { status ->
                whenever(repository.countByStatus(status, "local")).thenReturn(0)
                whenever(repository.countByStatus(status, "kafka"))
                    .thenReturn(if (status == OutboxEntryStatus.DEAD_LETTER) 4 else 0)
            }
            val state = SchedulerExecutionState().apply { recordSuccess(now) }
            val health =
                OutboxOperationalHealth(
                    repository,
                    state,
                    OutboxObservabilityProperties(Duration.ofSeconds(60), 1, 3, 3),
                    5,
                    clock,
                    setOf("local", "kafka"),
                )

            val snapshot = health.snapshot()
            snapshot.transports.getValue("local").status shouldBe OutboxOperationalStatus.HEALTHY
            snapshot.transports.getValue("kafka").status shouldBe OutboxOperationalStatus.DEGRADED

            val registry = SimpleMeterRegistry()
            MicrometerOutboxMonitor(
                registry,
                repository,
                health,
                state,
                setOf("local", "kafka"),
            )
            registry
                .get("jstore.outbox.oldest_ready.lag")
                .tag("transportId", "local")
                .gauge()
                .value()
                .shouldBeExactly(30.0)
            registry
                .get("jstore.outbox.oldest_ready.lag")
                .tag("transportId", "kafka")
                .gauge()
                .value()
                .shouldBeExactly(301.0)
            registry
                .get("jstore.outbox.alert")
                .tag("transportId", "kafka")
                .tag("reason", "dead_letter")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
        }

        test("scheduler failure gauge does not query the outbox repository") {
            val repository = mock<OutboxEntryRepository>()
            val registry = SimpleMeterRegistry()
            val state = SchedulerExecutionState()
            val properties = OutboxObservabilityProperties(schedulerFailureThreshold = 2)
            val health = OutboxOperationalHealth(repository, state, properties, 5, clock)
            MicrometerOutboxMonitor(
                registry,
                repository,
                health,
                state,
                schedulerFailureThreshold = properties.schedulerFailureThreshold,
            )
            clearInvocations(repository)
            repeat(2) { state.recordFailure(now) }

            registry
                .get("jstore.outbox.alert")
                .tag("reason", "scheduler_failure")
                .tag("transportId", "all")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
            verifyNoInteractions(repository)
        }
    })
