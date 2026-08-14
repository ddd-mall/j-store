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

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.util.UUID
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping

@Order(Ordered.HIGHEST_PRECEDENCE + 20)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val correlationId =
            acceptedHeader(request.getHeader(HEADER_NAME)) ?: UUID.randomUUID().toString()
        val previous = MDC.get(MDC_KEY)
        val startedAt = System.nanoTime()
        MDC.put(MDC_KEY, correlationId)
        response.setHeader(HEADER_NAME, correlationId)
        var failure: Throwable? = null
        try {
            filterChain.doFilter(request, response)
        } catch (thrown: Throwable) {
            failure = thrown
            throw thrown
        } finally {
            try {
                logCompletion(request, response, startedAt, failure)
            } finally {
                if (previous == null) MDC.remove(MDC_KEY) else MDC.put(MDC_KEY, previous)
            }
        }
    }

    private fun acceptedHeader(value: String?): String? = value?.takeIf { SAFE_VALUE.matches(it) }

    private fun logCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        startedAt: Long,
        failure: Throwable?,
    ) {
        val status = effectiveStatus(response.status, failure)
        val event =
            accessLogger
                .atInfo()
                .addKeyValue("event.name", "http.server.request")
                .addKeyValue("event.outcome", outcome(status))
                .addKeyValue("http.request.method", request.method)
                .addKeyValue("url.path", normalizedPath(request))
                .addKeyValue("http.response.status_code", status)
                .addKeyValue(
                    "http.response.duration_ms",
                    (System.nanoTime() - startedAt) / 1_000_000,
                )
        if (failure != null) {
            event
                .addKeyValue("error.type", failure.javaClass.name)
                .addKeyValue("error.stack_trace", diagnosticStackTrace(failure))
        }
        event.log("HTTP request completed")
    }

    private fun normalizedPath(request: HttpServletRequest): String =
        request
            .getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE)
            ?.toString()
            ?.takeIf(String::isNotBlank) ?: UNMATCHED_PATH

    private fun diagnosticStackTrace(failure: Throwable): String = buildString {
        appendDiagnosticStack(failure, mutableSetOf(), depth = 0)
    }

    private fun StringBuilder.appendDiagnosticStack(
        failure: Throwable,
        seen: MutableSet<Throwable>,
        depth: Int,
    ) {
        if (depth >= MAX_CAUSE_DEPTH) {
            append("cause chain truncated")
            return
        }
        if (!seen.add(failure)) {
            append("circular cause: ").append(failure.javaClass.name)
            return
        }
        append(failure.javaClass.name)
        failure.stackTrace.forEach { frame -> append("\n\tat ").append(frame) }
        failure.cause?.let { cause ->
            append("\nCaused by: ")
            appendDiagnosticStack(cause, seen, depth + 1)
        }
    }

    private fun effectiveStatus(status: Int, failure: Throwable?): Int =
        if (failure != null && status < 500) 500 else status

    private fun outcome(status: Int): String = if (status < 500) "success" else "failure"

    companion object {
        const val HEADER_NAME = "X-Correlation-ID"
        const val MDC_KEY = "correlation_id"
        private const val UNMATCHED_PATH = "UNMATCHED"
        private const val MAX_CAUSE_DEPTH = 8
        private val SAFE_VALUE = Regex("[A-Za-z0-9._:/-]{1,128}")
        private val accessLogger = LoggerFactory.getLogger(CorrelationIdFilter::class.java)
    }
}
