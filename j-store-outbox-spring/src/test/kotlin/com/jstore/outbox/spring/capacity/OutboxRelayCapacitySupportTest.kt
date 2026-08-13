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
package com.jstore.outbox.spring.capacity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

class OutboxRelayCapacitySupportTest :
    FunSpec({
        test("capacity configuration rejects invalid workload bounds") {
            shouldThrow<IllegalArgumentException> {
                OutboxRelayCapacityConfig(messageCount = 0)
            }
            shouldThrow<IllegalArgumentException> {
                OutboxRelayCapacityConfig(producerConcurrency = 0)
            }
            shouldThrow<IllegalArgumentException> {
                OutboxRelayCapacityConfig(timeout = Duration.ZERO)
            }
        }

        test("nearest-rank percentiles retain long-tail latency") {
            val samples = (1L..100L).map { Duration.ofMillis(it) }

            OutboxRelayCapacityReport.percentile(samples, 0.50) shouldBe Duration.ofMillis(50)
            OutboxRelayCapacityReport.percentile(samples, 0.95) shouldBe Duration.ofMillis(95)
            OutboxRelayCapacityReport.percentile(samples, 0.99) shouldBe Duration.ofMillis(99)
        }

        test("report exposes complete latency and throughput evidence") {
            val report =
                OutboxRelayCapacityReport.create(
                    config = OutboxRelayCapacityConfig(messageCount = 4),
                    latencies = listOf(10L, 20L, 30L, 40L).map(Duration::ofMillis),
                    startedAt = Instant.parse("2026-08-14T00:00:00Z"),
                    completedAt = Instant.parse("2026-08-14T00:00:02Z"),
                    elapsed = Duration.ofSeconds(2),
                )

            report.deliveredCount shouldBe 4
            report.p95Millis shouldBe 40.0
            report.p99Millis shouldBe 40.0
            report.throughputPerSecond shouldBe (2.0 plusOrMinus 0.001)
        }

        test("report rejects a non-positive monotonic elapsed duration") {
            shouldThrow<IllegalArgumentException> {
                OutboxRelayCapacityReport.create(
                    config = OutboxRelayCapacityConfig(messageCount = 1),
                    latencies = listOf(Duration.ofMillis(1)),
                    startedAt = Instant.parse("2026-08-14T00:00:00Z"),
                    completedAt = Instant.parse("2026-08-14T00:00:00Z"),
                    elapsed = Duration.ZERO,
                )
            }
        }
    })
