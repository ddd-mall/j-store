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

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

class OutboxHealthIndicator(private val snapshotProvider: () -> OutboxOperationalSnapshot) :
    HealthIndicator {

    override fun health(): Health {
        val snapshot = snapshotProvider()
        return Health.status(actuatorStatus(snapshot.status))
            .withDetails(healthDetails(snapshot))
            .build()
    }

    private fun actuatorStatus(status: OutboxOperationalStatus): Status =
        when (status) {
            OutboxOperationalStatus.HEALTHY -> Status.UP
            OutboxOperationalStatus.DEGRADED -> DEGRADED
            OutboxOperationalStatus.FAILED -> Status.DOWN
            OutboxOperationalStatus.NOT_RUN -> Status.UNKNOWN
        }

    private fun healthDetails(snapshot: OutboxOperationalSnapshot): Map<String, Any> =
        mapOf(
            "observedAt" to snapshot.observedAt.toString(),
            "oldestReadyLagSeconds" to snapshot.oldestReadyLag.seconds,
            "expiredLockCount" to snapshot.expiredLockCount,
            "deadLetterCount" to snapshot.deadLetterCount,
            "activeAlerts" to activeAlerts(snapshot),
            "scheduler" to schedulerDetails(snapshot),
            "transports" to transportDetails(snapshot),
        )

    private fun activeAlerts(snapshot: OutboxOperationalSnapshot): List<String> = buildList {
        if (snapshot.lagAlert) add("lag")
        if (snapshot.expiredLockAlert) add("expired_lock")
        if (snapshot.deadLetterAlert) add("dead_letter")
        if (snapshot.status == OutboxOperationalStatus.FAILED) add("scheduler_failure")
    }

    private fun schedulerDetails(snapshot: OutboxOperationalSnapshot): Map<String, Any> = buildMap {
        put("consecutiveFailures", snapshot.scheduler.consecutiveFailures)
        snapshot.scheduler.lastSuccessAt?.let { put("lastSuccessAt", it.toString()) }
        snapshot.scheduler.lastFailureAt?.let { put("lastFailureAt", it.toString()) }
    }

    private fun transportDetails(
        snapshot: OutboxOperationalSnapshot
    ): Map<String, Map<String, Any>> =
        snapshot.transports.toSortedMap().mapValues { (_, transport) ->
            mapOf(
                "status" to transport.status.name,
                "oldestReadyLagSeconds" to transport.oldestReadyLag.seconds,
                "expiredLockCount" to transport.expiredLockCount,
                "deadLetterCount" to transport.deadLetterCount,
                "activeAlerts" to
                    buildList {
                        if (transport.lagAlert) add("lag")
                        if (transport.expiredLockAlert) add("expired_lock")
                        if (transport.deadLetterAlert) add("dead_letter")
                    },
            )
        }

    private companion object {
        val DEGRADED = Status("DEGRADED")
    }
}

@AutoConfiguration(after = [OutboxAutoConfiguration::class])
@ConditionalOnClass(HealthIndicator::class)
@ConditionalOnBean(OutboxOperationalHealth::class)
class OutboxHealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = ["outboxHealthIndicator"])
    fun outboxHealthIndicator(operationalHealth: OutboxOperationalHealth): OutboxHealthIndicator =
        OutboxHealthIndicator(operationalHealth::snapshot)
}
