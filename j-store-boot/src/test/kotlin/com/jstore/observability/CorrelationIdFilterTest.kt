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

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.web.servlet.HandlerMapping

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    @AfterEach fun clearMdc() = MDC.clear()

    @Test
    fun `safe incoming correlation id is visible during request and returned to caller`() {
        val request = MockHttpServletRequest("GET", "/api/orders/42")
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "checkout-42")
        val response = MockHttpServletResponse()
        var observed: String? = null

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> observed = MDC.get(CorrelationIdFilter.MDC_KEY) },
        )

        assertThat(observed).isEqualTo("checkout-42")
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("checkout-42")
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull()
    }

    @Test
    fun `missing or unsafe incoming value is replaced with generated id`() {
        listOf(null, "contains spaces", "line\r\nbreak", "x".repeat(129)).forEach { incoming ->
            val request = MockHttpServletRequest("GET", "/api/orders")
            incoming?.let { request.addHeader(CorrelationIdFilter.HEADER_NAME, it) }
            val response = MockHttpServletResponse()
            var observed = ""

            filter.doFilter(
                request,
                response,
                FilterChain { _, _ -> observed = MDC.get(CorrelationIdFilter.MDC_KEY) },
            )

            assertThat(observed).matches("[0-9a-f-]{36}")
            assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(observed)
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull()
        }
    }

    @Test
    fun `failure path restores a pre-existing logging context`() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "outer-scope")
        val request = MockHttpServletRequest("POST", "/api/orders")
        val response = MockHttpServletResponse()

        runCatching {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ -> throw IllegalStateException("synthetic failure") },
            )
        }

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("outer-scope")
    }

    @Test
    fun `unhandled failure is logged with failure outcome effective status and stack`() {
        val logger = LoggerFactory.getLogger(CorrelationIdFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        val sensitiveValues =
            listOf(
                "+8613800138000",
                "synthetic-password",
                "eyJhbGciOiJub25lIn0.synthetic.jwt",
                "verification-code-123456",
                "synthetic-message-payload",
            )
        val request = MockHttpServletRequest("POST", "/api/orders")
        val response = MockHttpServletResponse()

        try {
            assertThatThrownBy {
                    filter.doFilter(
                        request,
                        response,
                        FilterChain { _, _ ->
                            throw IllegalStateException(sensitiveValues.joinToString(" | "))
                        },
                    )
                }
                .isInstanceOf(IllegalStateException::class.java)
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val event = appender.list.single()
        val fields = event.keyValuePairs.associate { it.key to it.value }
        assertThat(fields["event.outcome"]).isEqualTo("failure")
        assertThat(fields["http.response.status_code"]).isEqualTo(500)
        assertThat(fields["error.type"]).isEqualTo(IllegalStateException::class.java.name)
        assertThat(fields["error.stack_trace"].toString())
            .contains(IllegalStateException::class.java.name)
        sensitiveValues.forEach { sensitiveValue ->
            assertThat(fields["error.stack_trace"].toString()).doesNotContain(sensitiveValue)
        }
        assertThat(event.throwableProxy).isNull()
    }

    @Test
    fun `access log uses the matched route pattern instead of the raw request path`() {
        val logger = LoggerFactory.getLogger(CorrelationIdFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        val request = MockHttpServletRequest("GET", "/api/orders/42")
        val response = MockHttpServletResponse()

        try {
            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    request.setAttribute(
                        HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                        "/api/orders/{orderId}",
                    )
                },
            )
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }

        val fields = appender.list.single().keyValuePairs.associate { it.key to it.value }
        assertThat(fields["url.path"]).isEqualTo("/api/orders/{orderId}")
    }
}
