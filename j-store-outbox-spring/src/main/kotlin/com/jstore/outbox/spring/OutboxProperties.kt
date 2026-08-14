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

import org.springframework.boot.context.properties.ConfigurationProperties

/** Outbox 模式配置属性。 */
@ConfigurationProperties(prefix = "jstore.outbox")
data class OutboxProperties(
    val enabled: Boolean = false,
    val pollingInterval: Long = 5000,
    val batchSize: Int = 100,
    val maxInFlightPerPoll: Int = batchSize,
    val maxBatchesPerDrain: Int = 10,
    val maxRetryCount: Int = 5,
    val initialRetryDelayMillis: Long = 1000,
    val maxRetryDelayMillis: Long = 60000,
    val lockTimeoutMillis: Long = 300000,
    val workerId: String = "",
    val retentionDays: Int = 7,
    val consumptionRetentionDays: Int = 14,
    val cleanupBatchSize: Int = 500,
    val cleanupMaxBatchesPerRun: Int = 10,
    val cleanupIntervalMillis: Long = 60000,
    val eventTypeScanPackages: List<String> = listOf("com.jstore"),
    val asyncMulticasterFailFast: Boolean = false,
) {
    init {
        require(pollingInterval > 0) { "jstore.outbox.polling-interval must be greater than 0" }
        require(batchSize > 0) { "jstore.outbox.batch-size must be greater than 0" }
        require(maxInFlightPerPoll > 0) {
            "jstore.outbox.max-in-flight-per-poll must be greater than 0"
        }
        require(maxBatchesPerDrain > 0) {
            "jstore.outbox.max-batches-per-drain must be greater than 0"
        }
        require(maxRetryCount > 0) { "jstore.outbox.max-retry-count must be greater than 0" }
        require(initialRetryDelayMillis >= 0) {
            "jstore.outbox.initial-retry-delay-millis must be greater than or equal to 0"
        }
        require(maxRetryDelayMillis >= initialRetryDelayMillis) {
            "jstore.outbox.max-retry-delay-millis must be greater than or equal to initial retry delay"
        }
        require(lockTimeoutMillis > 0) {
            "jstore.outbox.lock-timeout-millis must be greater than 0"
        }
        require(retentionDays >= 0) {
            "jstore.outbox.retention-days must be greater than or equal to 0"
        }
        require(consumptionRetentionDays >= retentionDays) {
            "jstore.outbox.consumption-retention-days must be greater than or equal to retention-days"
        }
        require(cleanupBatchSize > 0) { "jstore.outbox.cleanup-batch-size must be greater than 0" }
        require(cleanupMaxBatchesPerRun > 0) {
            "jstore.outbox.cleanup-max-batches-per-run must be greater than 0"
        }
        require(cleanupIntervalMillis > 0) {
            "jstore.outbox.cleanup-interval-millis must be greater than 0"
        }
        require(eventTypeScanPackages.isNotEmpty()) {
            "jstore.outbox.event-type-scan-packages must not be empty"
        }
    }
}
