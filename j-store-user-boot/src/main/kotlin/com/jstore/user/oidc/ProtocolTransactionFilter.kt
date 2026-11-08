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
package com.jstore.user.oidc

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper

/** Finish credential persistence before making a token or redirect visible to the caller. */
class ProtocolTransactionFilter(manager: PlatformTransactionManager) : OncePerRequestFilter() {
    private val transaction = TransactionTemplate(manager)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val buffered =
            object : ContentCachingResponseWrapper(response) {
                override fun sendRedirect(location: String) {
                    status = 302
                    setHeader("Location", location)
                }

                override fun sendError(code: Int) {
                    status = code
                }

                override fun sendError(code: Int, message: String?) {
                    status = code
                }
            }
        try {
            transaction.executeWithoutResult { chain.doFilter(request, buffered) }
            buffered.copyBodyToResponse()
        } catch (_: Exception) {
            if (!response.isCommitted) {
                response.reset()
                response.status = 503
                response.contentType = "application/json"
                response.writer.write("{\"error\":\"temporarily_unavailable\"}")
            }
        }
    }
}
