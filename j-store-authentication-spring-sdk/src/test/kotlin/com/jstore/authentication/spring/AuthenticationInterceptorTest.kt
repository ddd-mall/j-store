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

import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.config.AuthenticationConfigurer
import com.jstore.authentication.error.AuthenticationErrors
import com.jstore.authentication.principal.AccessTokenVerifier
import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.authentication.principal.AuthenticatedSession
import com.jstore.authentication.principal.AuthenticatedSessionStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.PrintWriter
import java.io.StringWriter
import org.mockito.kotlin.*
import org.springframework.web.method.HandlerMethod

class AuthenticationInterceptorTest :
    FunSpec({

        // _需求: 6.4_ — 非预期异常返回 HTTP 500 + Auth.InternalError，不泄露异常详情
        test(
            "unexpected exception during token validation returns HTTP 500 with Auth.InternalError"
        ) {
            val tokenVerifier = mock<AccessTokenVerifier>()
            val tokenStore = mock<AuthenticatedSessionStore>()
            val interceptor =
                AuthenticationInterceptor(
                    accessTokenVerifier = tokenVerifier,
                    tokenStore = tokenStore,
                    configurers = emptyList(),
                )

            val handlerMethod = mock<HandlerMethod>()
            whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java)).thenReturn(true)

            val request = mock<HttpServletRequest>()
            whenever(request.getHeader("Authorization")).thenReturn("Bearer sometoken")
            whenever(request.requestURI).thenReturn("/api/test")

            // parseAccessToken throws an unexpected RuntimeException
            whenever(tokenVerifier.verifyAccessToken("sometoken"))
                .thenThrow(RuntimeException("unexpected DB connection failure"))

            val stringWriter = StringWriter()
            val response = mock<HttpServletResponse>()
            whenever(response.writer).thenReturn(PrintWriter(stringWriter))

            val result = interceptor.preHandle(request, response, handlerMethod)

            result shouldBe false
            verify(response).status = 500
            verify(response).contentType = "application/json"

            val body = stringWriter.toString()
            body shouldContain "\"errorCode\""
            body shouldContain "Auth.InternalError"
            body shouldContain "\"message\""
            body shouldContain AuthenticationErrors.INTERNAL_ERROR.message

            // Exception details must NOT be leaked in the response
            body shouldNotContain "unexpected DB connection failure"
            body shouldNotContain "RuntimeException"
        }

        // _需求: 3.1_ — 注解 + 路径配置同时满足时只执行一次验证
        test(
            "when both annotation and path config require auth, token validation executes only once"
        ) {
            val tokenVerifier = mock<AccessTokenVerifier>()
            val tokenStore = mock<AuthenticatedSessionStore>()

            // Configure a path pattern that also matches the request
            val configurer =
                object : AuthenticationConfigurer {
                    override fun authenticatedPathPatterns(): List<String> = listOf("/api/**")

                    override fun excludedPathPatterns(): List<String> = emptyList()
                }

            val interceptor =
                AuthenticationInterceptor(
                    accessTokenVerifier = tokenVerifier,
                    tokenStore = tokenStore,
                    configurers = listOf(configurer),
                )

            // HandlerMethod with @RequireLogin on method
            val handlerMethod = mock<HandlerMethod>()
            whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java)).thenReturn(true)

            val request = mock<HttpServletRequest>()
            whenever(request.getHeader("Authorization")).thenReturn("Bearer validtoken")
            whenever(request.requestURI).thenReturn("/api/orders")

            // Mock valid token flow
            val principal =
                AuthenticatedPrincipal(
                    "issuer-a",
                    AuthenticatedAccountId(42L),
                    AuthenticatedSession("session-1", 2L),
                )
            whenever(tokenVerifier.verifyAccessToken("validtoken")).thenReturn(principal)
            whenever(tokenStore.isSessionActive(AuthenticatedAccountId(42L), "session-1", 2L))
                .thenReturn(true)

            val response = mock<HttpServletResponse>()

            val result = interceptor.preHandle(request, response, handlerMethod)

            result shouldBe true

            // parseAccessToken should be called exactly once — not twice for annotation + path
            verify(tokenVerifier, times(1)).verifyAccessToken("validtoken")
        }
    })
