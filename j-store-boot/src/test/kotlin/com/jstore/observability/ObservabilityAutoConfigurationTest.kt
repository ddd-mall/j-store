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

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import io.opentelemetry.api.OpenTelemetry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.observation.web.client.HttpClientObservationsAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.opentelemetry.OpenTelemetryAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.tracing.MicrometerTracingAutoConfiguration
import org.springframework.boot.actuate.autoconfigure.tracing.OpenTelemetryTracingAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.client.RestClient

class ObservabilityAutoConfigurationTest {
    private val runner =
        ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    MetricsAutoConfiguration::class.java,
                    CompositeMeterRegistryAutoConfiguration::class.java,
                    PrometheusMetricsExportAutoConfiguration::class.java,
                    ObservationAutoConfiguration::class.java,
                    OpenTelemetryAutoConfiguration::class.java,
                    OpenTelemetryTracingAutoConfiguration::class.java,
                    MicrometerTracingAutoConfiguration::class.java,
                    RestClientAutoConfiguration::class.java,
                    HttpClientObservationsAutoConfiguration::class.java,
                )
            )

    @Test
    fun `OpenTelemetry runtime contains the baggage allocation fix`() {
        val runtimeVersion =
            requireNotNull(OpenTelemetry::class.java.`package`.implementationVersion)
        val versionParts = runtimeVersion.split(".").map(String::toInt)

        assertThat(versionParts[0] > 1 || (versionParts[0] == 1 && versionParts[1] >= 62)).isTrue()
    }

    @Test
    fun `OpenTelemetry tracer Prometheus registry and observed RestClient builder are configured`() {
        runner.run { context ->
            assertThat(context).hasNotFailed()
            assertThat(context).hasSingleBean(Tracer::class.java)
            assertThat(context).hasSingleBean(RestClient.Builder::class.java)
            assertThat(context.getBeansOfType(MeterRegistry::class.java).values).anyMatch {
                it.javaClass.simpleName.contains("PrometheusMeterRegistry")
            }

            val tracer = context.getBean(Tracer::class.java)
            val span = tracer.startScopedSpan("contract-span")
            try {
                val currentSpan = requireNotNull(tracer.currentSpan())
                assertThat(currentSpan.context().traceId()).isEqualTo(span.context().traceId())
                assertThat(currentSpan.context().spanId()).isEqualTo(span.context().spanId())
                val headers = mutableMapOf<String, String>()
                context.getBean(Propagator::class.java).inject(span.context(), headers) {
                    carrier,
                    key,
                    value ->
                    carrier!![key] = value
                }
                assertThat(headers["traceparent"]).matches("00-[0-9a-f]{32}-[0-9a-f]{16}-0[0-3]")
            } finally {
                span.end()
            }
        }
    }
}
