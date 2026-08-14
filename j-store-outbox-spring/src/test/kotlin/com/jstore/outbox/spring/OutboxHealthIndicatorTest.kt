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

import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.actuate.health.Status
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class OutboxHealthIndicatorTest {
    private val now = Instant.parse("2026-08-14T03:00:00Z")

    @Test
    fun `maps every operational status without changing probe semantics`() {
        val mappings =
            mapOf(
                OutboxOperationalStatus.HEALTHY to Status.UP,
                OutboxOperationalStatus.DEGRADED to Status("DEGRADED"),
                OutboxOperationalStatus.FAILED to Status.DOWN,
                OutboxOperationalStatus.NOT_RUN to Status.UNKNOWN,
            )

        mappings.forEach { (operationalStatus, actuatorStatus) ->
            val indicator = OutboxHealthIndicator { snapshot(operationalStatus) }

            assertThat(indicator.health().status).isEqualTo(actuatorStatus)
        }
    }

    @Test
    fun `exposes only bounded operational details`() {
        val indicator = OutboxHealthIndicator {
            snapshot(
                status = OutboxOperationalStatus.DEGRADED,
                lagAlert = true,
                expiredLockAlert = true,
                deadLetterAlert = true,
            )
        }

        val health = indicator.health()

        assertThat(health.details)
            .containsEntry("observedAt", now.toString())
            .containsEntry("oldestReadyLagSeconds", 90L)
            .containsEntry("expiredLockCount", 2L)
            .containsEntry("deadLetterCount", 3L)
        assertThat(health.details["activeAlerts"])
            .isEqualTo(listOf("lag", "expired_lock", "dead_letter"))
        assertThat(health.details.keys)
            .containsExactlyInAnyOrder(
                "observedAt",
                "oldestReadyLagSeconds",
                "expiredLockCount",
                "deadLetterCount",
                "activeAlerts",
                "scheduler",
                "transports",
            )
        assertThat(health.details.toString())
            .doesNotContain("payload", "aggregateId", "messageId", "userId", "secret", "token")
    }

    @Test
    fun `registers the indicator only when operational health exists`() {
        val runner =
            ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxHealthAutoConfiguration::class.java))

        runner.run { context ->
            assertThat(context).doesNotHaveBean(OutboxHealthIndicator::class.java)
        }
        runner
            .withBean(
                OutboxOperationalHealth::class.java,
                { mock(OutboxOperationalHealth::class.java) },
            )
            .run { context ->
                assertThat(context).hasSingleBean(OutboxHealthIndicator::class.java)
                assertThat(context).hasBean("outboxHealthIndicator")
            }
    }

    @Test
    fun `runtime without actuator can still load outbox auto configuration`() {
        ApplicationContextRunner()
            .withClassLoader(FilteredClassLoader("org.springframework.boot.actuate"))
            .withConfiguration(AutoConfigurations.of(OutboxHealthAutoConfiguration::class.java))
            .withBean(
                OutboxOperationalHealth::class.java,
                { mock(OutboxOperationalHealth::class.java) },
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(OutboxHealthIndicator::class.java)
            }
    }

    private fun snapshot(
        status: OutboxOperationalStatus,
        lagAlert: Boolean = false,
        expiredLockAlert: Boolean = false,
        deadLetterAlert: Boolean = false,
    ): OutboxOperationalSnapshot =
        OutboxOperationalSnapshot(
            status = status,
            observedAt = now,
            oldestReadyLag = Duration.ofSeconds(90),
            expiredLockCount = 2,
            deadLetterCount = 3,
            lagAlert = lagAlert,
            expiredLockAlert = expiredLockAlert,
            deadLetterAlert = deadLetterAlert,
            scheduler =
                SchedulerExecutionSnapshot(
                    lastSuccessAt = now.minusSeconds(30),
                    lastFailureAt = now.minusSeconds(10),
                    consecutiveFailures = 1,
                ),
        )
}
