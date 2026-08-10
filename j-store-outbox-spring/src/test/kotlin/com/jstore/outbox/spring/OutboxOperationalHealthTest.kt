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
import org.mockito.kotlin.mock
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
                )
            monitor.recordDelivery(kafkaEntry, true)

            registry.get("jstore.outbox.oldest_ready.lag").gauge().value().shouldBeExactly(120.0)
            registry.get("jstore.outbox.expired_locks").gauge().value().shouldBeExactly(2.0)
            registry
                .get("jstore.outbox.alert")
                .tag("reason", "lag")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
            registry
                .get("jstore.outbox.alert")
                .tag("reason", "expired_lock")
                .gauge()
                .value()
                .shouldBeExactly(1.0)
            registry
                .get("jstore.outbox.alert")
                .tag("reason", "dead_letter")
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
        }
    })
