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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import java.util.Properties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.event.KeyValuePair
import org.springframework.boot.logging.logback.StructuredLogEncoder
import org.springframework.core.env.Environment
import org.springframework.mock.env.MockEnvironment

class StructuredLoggingContractTest {
    @Test
    fun `observability profile enables ECS output with stable service metadata`() {
        val properties = Properties()
        javaClass.getResourceAsStream("/application-observability.properties").use { stream ->
            requireNotNull(stream) { "application-observability.properties must exist" }
            properties.load(stream)
        }

        assertThat(properties.getProperty("logging.structured.format.console")).isEqualTo("ecs")
        assertThat(properties.getProperty("logging.structured.ecs.service.name"))
            .isEqualTo("${'$'}{spring.application.name}")
        assertThat(properties.getProperty("logging.structured.ecs.service.version")).isNotBlank()
        assertThat(properties.getProperty("logging.structured.ecs.service.environment"))
            .isNotBlank()
    }

    @Test
    fun `ECS encoder emits one machine readable event and includes MDC fields`() {
        val context = LoggerContext()
        context.putObject(
            Environment::class.java.name,
            MockEnvironment()
                .withProperty("spring.application.name", "j-store")
                .withProperty("logging.structured.ecs.service.version", "test")
                .withProperty("logging.structured.ecs.service.environment", "contract"),
        )
        val encoder = StructuredLogEncoder()
        encoder.context = context
        encoder.setFormat("ecs")
        encoder.start()
        val event =
            LoggingEvent().apply {
                loggerName = "com.jstore.observability.contract"
                level = Level.INFO
                message = "structured-contract-marker"
                timeStamp = 1_786_450_800_000
                mdcPropertyMap = mapOf("correlation_id" to "checkout-42")
                keyValuePairs =
                    listOf(
                        KeyValuePair("error.type", "java.lang.IllegalStateException"),
                        KeyValuePair(
                            "error.stack_trace",
                            "java.lang.IllegalStateException\n\tat test",
                        ),
                    )
            }

        val encoded = String(encoder.encode(event), StandardCharsets.UTF_8)
        val json = ObjectMapper().readTree(encoded)

        assertThat(encoded.count { it == '\n' }).isEqualTo(1)
        assertThat(json.path("log").path("level").asText()).isEqualTo("INFO")
        assertThat(json.path("log").path("logger").asText())
            .isEqualTo("com.jstore.observability.contract")
        assertThat(json.path("message").asText()).isEqualTo("structured-contract-marker")
        assertThat(json.path("correlation_id").asText()).isEqualTo("checkout-42")
        assertThat(json.path("service").path("name").asText()).isEqualTo("j-store")
        assertThat(json.path("service").path("version").asText()).isEqualTo("test")
        assertThat(json.path("service").path("environment").asText()).isEqualTo("contract")
        assertThat(json.path("error").path("type").asText())
            .isEqualTo("java.lang.IllegalStateException")
        assertThat(json.path("error").path("stack_trace").asText())
            .contains("java.lang.IllegalStateException")
    }
}
