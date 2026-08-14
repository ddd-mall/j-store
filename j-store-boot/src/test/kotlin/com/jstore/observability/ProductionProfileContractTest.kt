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
package com.jstore.observability

import java.util.Properties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProductionProfileContractTest {

    @Test
    fun `profiles are explicit and production composes observability`() {
        val root = loadProperties("/application.properties")

        assertThat(root).doesNotContainKey("spring.profiles.active")
        assertThat(root.getProperty("spring.profiles.group.production"))
            .isEqualTo("observability,outbox-observability")
    }

    @Test
    fun `production requires external connections and secrets`() {
        val production = loadProperties("/application-production.properties")

        val requiredExternalProperties =
            mapOf(
                "spring.datasource.url" to "\${JSTORE_DB_URL}",
                "spring.datasource.username" to "\${JSTORE_DB_USER}",
                "spring.datasource.password" to "\${JSTORE_DB_PASSWORD}",
                "spring.data.redis.host" to "\${JSTORE_REDIS_HOST}",
                "spring.data.redis.password" to "\${JSTORE_REDIS_PASSWORD}",
                "jwt.access-secret" to "\${JSTORE_JWT_ACCESS_SECRET}",
                "jwt.refresh-secret" to "\${JSTORE_JWT_REFRESH_SECRET}",
                "account.phone-verification.hmac-secret" to
                    "\${JSTORE_PHONE_VERIFICATION_HMAC_SECRET}",
            )
        requiredExternalProperties.forEach { (name, expected) ->
            assertThat(production.getProperty(name)).describedAs(name).isEqualTo(expected)
        }
        assertThat(production.getProperty("spring.datasource.hikari.pool-name"))
            .isEqualTo("j-store")
        assertThat(production.getProperty("spring.jpa.open-in-view")).isEqualTo("false")
    }

    @Test
    fun `local identity and health groups cannot leak outbox into probes`() {
        val local = loadProperties("/application-local.properties")
        val observability = loadProperties("/application-observability.properties")
        val outboxObservability = loadProperties("/application-outbox-observability.properties")

        assertThat(local.getProperty("spring.datasource.hikari.pool-name")).isEqualTo("j-store")
        assertThat(observability.getProperty("management.endpoint.health.group.liveness.include"))
            .isEqualTo("livenessState")
        assertThat(observability.getProperty("management.endpoint.health.group.readiness.include"))
            .isEqualTo("readinessState")
        assertThat(observability)
            .doesNotContainKey("management.endpoint.health.group.operations.include")
        assertThat(
                outboxObservability.getProperty(
                    "management.endpoint.health.group.operations.include"
                )
            )
            .isEqualTo("outbox")
    }

    private fun loadProperties(resource: String): Properties =
        Properties().apply {
            ProductionProfileContractTest::class.java.getResourceAsStream(resource).use { stream ->
                requireNotNull(stream) { "$resource must exist" }
                load(stream)
            }
        }
}
