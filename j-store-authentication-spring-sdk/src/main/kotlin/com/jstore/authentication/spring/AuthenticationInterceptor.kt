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
package com.jstore.authentication.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.annotation.SkipLogin
import com.jstore.authentication.config.AuthenticationConfigurer
import com.jstore.authentication.context.AuthenticatedUserContext
import com.jstore.authentication.error.AuthenticationErrors
import com.jstore.common.errors.BusinessError
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.util.AntPathMatcher
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

class AuthenticationInterceptor(
    private val tokenProvider: TokenProvider,
    private val tokenStore: TokenStore,
    private val configurers: List<AuthenticationConfigurer>,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) : HandlerInterceptor {

    private val pathMatcher = AntPathMatcher()

    private val authenticatedPatterns: List<String> by lazy {
        configurers.flatMap { it.authenticatedPathPatterns() }
    }

    private val excludedPatterns: List<String> by lazy {
        configurers.flatMap { it.excludedPathPatterns() }
    }

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
    ): Boolean {
        if (handler !is HandlerMethod) return true

        if (!requiresAuthentication(handler, request)) return true

        try {
            val token = extractBearerToken(request)
            if (token == null) {
                writeErrorResponse(response, AuthenticationErrors.TOKEN_MISSING)
                return false
            }

            val claims = tokenProvider.parseAccessToken(token)
            if (claims == null) {
                writeErrorResponse(response, AuthenticationErrors.TOKEN_INVALID)
                return false
            }

            if (!tokenStore.isSessionActive(
                    claims.userId,
                    claims.sessionId,
                    claims.sessionEpoch,
                )
            ) {
                writeErrorResponse(response, AuthenticationErrors.TOKEN_REVOKED)
                return false
            }

            AuthenticatedUserContext.set(claims.userId)
            return true
        } catch (_: Exception) {
            writeErrorResponse(response, AuthenticationErrors.INTERNAL_ERROR)
            return false
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        AuthenticatedUserContext.clear()
    }

    internal fun requiresAuthentication(
        handlerMethod: HandlerMethod,
        request: HttpServletRequest,
    ): Boolean {
        // 1. @SkipLogin → false（最高优先级）
        if (handlerMethod.hasMethodAnnotation(SkipLogin::class.java)) return false

        // 2. @RequireLogin（方法级或类级）→ true
        if (handlerMethod.hasMethodAnnotation(RequireLogin::class.java)) return true
        if (handlerMethod.beanType.isAnnotationPresent(RequireLogin::class.java)) return true

        val requestPath = request.requestURI

        // 3. 路径排除模式 → false
        if (matchesAnyPattern(requestPath, excludedPatterns)) return false

        // 4. 路径认证模式 → true
        if (matchesAnyPattern(requestPath, authenticatedPatterns)) return true

        // 5. 默认放行
        return false
    }

    private fun extractBearerToken(request: HttpServletRequest): String? {
        val header = request.getHeader("Authorization") ?: return null
        return if (header.startsWith("Bearer ")) header.substring(7) else null
    }

    private fun writeErrorResponse(response: HttpServletResponse, error: BusinessError) {
        response.status = error.httpCode
        response.contentType = "application/json"
        response.characterEncoding = "UTF-8"
        val body = mapOf("message" to error.message, "errorCode" to error.errorCode)
        response.writer.write(objectMapper.writeValueAsString(body))
    }

    private fun matchesAnyPattern(path: String, patterns: List<String>): Boolean {
        return patterns.any { pattern -> pathMatcher.match(pattern, path) }
    }
}
