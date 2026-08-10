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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jstore.outbox.observability")
data class OutboxObservabilityProperties(
    val lagThreshold: Duration = Duration.ofMinutes(5),
    val expiredLockThreshold: Long = 1,
    val deadLetterThreshold: Long = 1,
    val schedulerFailureThreshold: Int = 3,
) {
    init {
        require(!lagThreshold.isNegative && !lagThreshold.isZero) {
            "jstore.outbox.observability.lag-threshold must be greater than 0"
        }
        require(expiredLockThreshold > 0) {
            "jstore.outbox.observability.expired-lock-threshold must be greater than 0"
        }
        require(deadLetterThreshold > 0) {
            "jstore.outbox.observability.dead-letter-threshold must be greater than 0"
        }
        require(schedulerFailureThreshold > 0) {
            "jstore.outbox.observability.scheduler-failure-threshold must be greater than 0"
        }
    }
}

data class SchedulerExecutionSnapshot(
    val lastSuccessAt: Instant?,
    val lastFailureAt: Instant?,
    val consecutiveFailures: Int,
)

class SchedulerExecutionState {
    private val lastSuccessAt = AtomicReference<Instant?>()
    private val lastFailureAt = AtomicReference<Instant?>()
    private val consecutiveFailures = AtomicInteger()

    fun recordSuccess(at: Instant) {
        lastSuccessAt.set(at)
        consecutiveFailures.set(0)
    }

    fun recordFailure(at: Instant) {
        lastFailureAt.set(at)
        consecutiveFailures.incrementAndGet()
    }

    fun snapshot(): SchedulerExecutionSnapshot =
        SchedulerExecutionSnapshot(
            lastSuccessAt = lastSuccessAt.get(),
            lastFailureAt = lastFailureAt.get(),
            consecutiveFailures = consecutiveFailures.get(),
        )
}

enum class OutboxOperationalStatus {
    NOT_RUN,
    HEALTHY,
    DEGRADED,
    FAILED,
}

data class OutboxOperationalSnapshot(
    val status: OutboxOperationalStatus,
    val observedAt: Instant,
    val oldestReadyLag: Duration,
    val expiredLockCount: Long,
    val deadLetterCount: Long,
    val lagAlert: Boolean,
    val expiredLockAlert: Boolean,
    val deadLetterAlert: Boolean,
    val scheduler: SchedulerExecutionSnapshot,
    val transports: Map<String, OutboxTransportOperationalSnapshot> = emptyMap(),
)

data class OutboxTransportOperationalSnapshot(
    val transportId: String,
    val status: OutboxOperationalStatus,
    val oldestReadyLag: Duration,
    val expiredLockCount: Long,
    val deadLetterCount: Long,
    val lagAlert: Boolean,
    val expiredLockAlert: Boolean,
    val deadLetterAlert: Boolean,
)

class OutboxOperationalHealth(
    private val repository: OutboxEntryRepository,
    private val schedulerState: SchedulerExecutionState,
    private val properties: OutboxObservabilityProperties,
    private val maxRetryCount: Int,
    private val clock: Clock = Clock.systemUTC(),
    private val configuredTransportIds: Set<String> = emptySet(),
) {
    fun snapshot(): OutboxOperationalSnapshot {
        val now = clock.instant()
        val oldestReadyAt = repository.findOldestReadyAt(now, maxRetryCount)
        val lag =
            oldestReadyAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }
                ?: Duration.ZERO
        val expiredLocks = repository.countExpiredLocks(now)
        val deadLetters = repository.countByStatus(OutboxEntryStatus.DEAD_LETTER)
        val scheduler = schedulerState.snapshot()
        val transports =
            (configuredTransportIds + repository.findTransportIds()).sorted().associateWith {
                transportId ->
                transportSnapshot(transportId, now, scheduler)
            }
        val lagAlert = lag >= properties.lagThreshold
        val expiredLockAlert = expiredLocks >= properties.expiredLockThreshold
        val deadLetterAlert = deadLetters >= properties.deadLetterThreshold
        val status =
            when {
                scheduler.consecutiveFailures >= properties.schedulerFailureThreshold ->
                    OutboxOperationalStatus.FAILED
                scheduler.lastSuccessAt == null && scheduler.lastFailureAt == null ->
                    OutboxOperationalStatus.NOT_RUN
                scheduler.consecutiveFailures > 0 ||
                    lagAlert ||
                    expiredLockAlert ||
                    deadLetterAlert -> OutboxOperationalStatus.DEGRADED
                else -> OutboxOperationalStatus.HEALTHY
            }
        return OutboxOperationalSnapshot(
            status,
            now,
            lag,
            expiredLocks,
            deadLetters,
            lagAlert,
            expiredLockAlert,
            deadLetterAlert,
            scheduler,
            transports,
        )
    }

    private fun transportSnapshot(
        transportId: String,
        now: Instant,
        scheduler: SchedulerExecutionSnapshot,
    ): OutboxTransportOperationalSnapshot {
        val oldestReadyAt = repository.findOldestReadyAt(now, maxRetryCount, transportId)
        val lag =
            oldestReadyAt?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) }
                ?: Duration.ZERO
        val expiredLocks = repository.countExpiredLocks(now, transportId)
        val deadLetters = repository.countByStatus(OutboxEntryStatus.DEAD_LETTER, transportId)
        val lagAlert = lag >= properties.lagThreshold
        val expiredLockAlert = expiredLocks >= properties.expiredLockThreshold
        val deadLetterAlert = deadLetters >= properties.deadLetterThreshold
        val status =
            when {
                scheduler.consecutiveFailures >= properties.schedulerFailureThreshold ->
                    OutboxOperationalStatus.FAILED
                scheduler.lastSuccessAt == null && scheduler.lastFailureAt == null ->
                    OutboxOperationalStatus.NOT_RUN
                scheduler.consecutiveFailures > 0 ||
                    lagAlert ||
                    expiredLockAlert ||
                    deadLetterAlert -> OutboxOperationalStatus.DEGRADED
                else -> OutboxOperationalStatus.HEALTHY
            }
        return OutboxTransportOperationalSnapshot(
            transportId,
            status,
            lag,
            expiredLocks,
            deadLetters,
            lagAlert,
            expiredLockAlert,
            deadLetterAlert,
        )
    }
}

private fun Duration.coerceAtLeast(minimum: Duration): Duration =
    if (this < minimum) minimum else this
