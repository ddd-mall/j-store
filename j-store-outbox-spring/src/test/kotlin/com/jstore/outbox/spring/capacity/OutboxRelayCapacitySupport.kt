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

import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

data class OutboxRelayCapacityConfig(
    val messageCount: Int = 1_000,
    val producerConcurrency: Int = 8,
    val batchSize: Int = 100,
    val maxBatchesPerDrain: Int = 10,
    val timeout: Duration = Duration.ofSeconds(60),
) {
    init {
        require(messageCount > 0) { "messageCount must be greater than 0" }
        require(producerConcurrency > 0) { "producerConcurrency must be greater than 0" }
        require(batchSize > 0) { "batchSize must be greater than 0" }
        require(maxBatchesPerDrain > 0) { "maxBatchesPerDrain must be greater than 0" }
        require(!timeout.isZero && !timeout.isNegative) { "timeout must be greater than 0" }
    }

    companion object {
        fun fromSystemProperties(): OutboxRelayCapacityConfig =
            OutboxRelayCapacityConfig(
                messageCount = integerProperty("outboxCapacity.messageCount", 1_000),
                producerConcurrency = integerProperty("outboxCapacity.producerConcurrency", 8),
                batchSize = integerProperty("outboxCapacity.batchSize", 100),
                maxBatchesPerDrain = integerProperty("outboxCapacity.maxBatchesPerDrain", 10),
                timeout =
                    Duration.ofSeconds(
                        integerProperty("outboxCapacity.timeoutSeconds", 60).toLong()
                    ),
            )

        private fun integerProperty(name: String, defaultValue: Int): Int =
            System.getProperty(name)?.toIntOrNull()
                ?: if (System.getProperty(name) == null) defaultValue
                else throw IllegalArgumentException("$name must be an integer")
    }
}

data class OutboxRelayCapacityReport(
    val expectedCount: Int,
    val deliveredCount: Int,
    val producerConcurrency: Int,
    val batchSize: Int,
    val maxBatchesPerDrain: Int,
    val startedAt: Instant,
    val completedAt: Instant,
    val elapsedMillis: Long,
    val minMillis: Double,
    val p50Millis: Double,
    val p95Millis: Double,
    val p99Millis: Double,
    val maxMillis: Double,
    val throughputPerSecond: Double,
) {
    companion object {
        fun create(
            config: OutboxRelayCapacityConfig,
            latencies: List<Duration>,
            startedAt: Instant,
            completedAt: Instant,
            elapsed: Duration,
        ): OutboxRelayCapacityReport {
            require(latencies.isNotEmpty()) { "latencies must not be empty" }
            require(!completedAt.isBefore(startedAt)) {
                "completedAt must not precede startedAt"
            }
            require(!elapsed.isZero && !elapsed.isNegative) {
                "elapsed must be greater than 0"
            }
            val elapsedSeconds = elapsed.toNanos() / 1_000_000_000.0
            return OutboxRelayCapacityReport(
                expectedCount = config.messageCount,
                deliveredCount = latencies.size,
                producerConcurrency = config.producerConcurrency,
                batchSize = config.batchSize,
                maxBatchesPerDrain = config.maxBatchesPerDrain,
                startedAt = startedAt,
                completedAt = completedAt,
                elapsedMillis = elapsed.toMillis(),
                minMillis = percentile(latencies, 0.0).toNanos() / 1_000_000.0,
                p50Millis = percentile(latencies, 0.50).toNanos() / 1_000_000.0,
                p95Millis = percentile(latencies, 0.95).toNanos() / 1_000_000.0,
                p99Millis = percentile(latencies, 0.99).toNanos() / 1_000_000.0,
                maxMillis = percentile(latencies, 1.0).toNanos() / 1_000_000.0,
                throughputPerSecond = latencies.size / elapsedSeconds,
            )
        }

        fun percentile(samples: List<Duration>, percentile: Double): Duration {
            require(samples.isNotEmpty()) { "samples must not be empty" }
            require(percentile in 0.0..1.0) { "percentile must be between 0 and 1" }
            val sorted = samples.sorted()
            val rank = ceil(percentile * sorted.size).toInt().coerceAtLeast(1)
            return sorted[rank - 1]
        }
    }
}
