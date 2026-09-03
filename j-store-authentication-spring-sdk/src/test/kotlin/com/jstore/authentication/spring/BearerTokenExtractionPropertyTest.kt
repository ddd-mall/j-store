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
import com.jstore.authentication.error.AuthenticationErrors
import com.jstore.authentication.principal.AccessTokenVerifier
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.authentication.principal.AuthenticatedSession
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.PrintWriter
import java.io.StringWriter
import org.mockito.kotlin.*
import org.springframework.web.method.HandlerMethod

// Feature: authentication-sdk, Property 2: Bearer Token 提取
@OptIn(ExperimentalKotest::class)
class BearerTokenExtractionPropertyTest :
    FunSpec({

        // **Validates: Requirements 4.1, 4.2**

        test("Bearer prefix + token extracts token and proceeds to parse") {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.string(minSize = 1, maxSize = 50).filter { !it.contains('\u0000') },
            ) { token ->
                // --- Build mocks ---
                val tokenVerifier = mock<AccessTokenVerifier>()
                val tokenStore = mock<TokenStore>()
                val interceptor =
                    AuthenticationInterceptor(
                        accessTokenVerifier = tokenVerifier,
                        tokenStore = tokenStore,
                        configurers = emptyList(),
                    )

                val handlerMethod = mock<HandlerMethod>()
                // Make this endpoint require login so preHandle enters token validation
                whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java))
                    .thenReturn(true)

                val request = mock<HttpServletRequest>()
                whenever(request.getHeader("Authorization")).thenReturn("Bearer $token")
                whenever(request.requestURI).thenReturn("/api/test")

                // parseAccessToken returns a UserId → token was successfully extracted
                val userId = UserId(1L)
                val principal =
                    AuthenticatedPrincipal(
                        "issuer-a",
                        userId,
                        AuthenticatedSession("session-1", 0L),
                    )
                whenever(tokenVerifier.verifyAccessToken(token)).thenReturn(principal)
                whenever(tokenStore.isSessionActive(userId, "session-1", 0L)).thenReturn(true)

                val response = mock<HttpServletResponse>()

                // --- Act ---
                val result = interceptor.preHandle(request, response, handlerMethod)

                // --- Assert ---
                // preHandle returns true → token was extracted and parsed successfully
                result shouldBe true
                // Verify parseAccessToken was called with the exact extracted token
                verify(tokenVerifier).verifyAccessToken(token)
            }
        }

        test("missing, empty, or non-Bearer Authorization header returns TOKEN_MISSING") {
            val invalidHeaders: List<String?> =
                listOf(null, "", "Basic abc123", "bearer token", "Token xyz", "BearerNoSpace")

            checkAll(
                PropTestConfig(iterations = 100),
                Arb.element(invalidHeaders),
            ) { headerValue ->
                // --- Build mocks ---
                val tokenVerifier = mock<AccessTokenVerifier>()
                val tokenStore = mock<TokenStore>()
                val interceptor =
                    AuthenticationInterceptor(
                        accessTokenVerifier = tokenVerifier,
                        tokenStore = tokenStore,
                        configurers = emptyList(),
                    )

                val handlerMethod = mock<HandlerMethod>()
                whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java))
                    .thenReturn(true)

                val request = mock<HttpServletRequest>()
                whenever(request.getHeader("Authorization")).thenReturn(headerValue)
                whenever(request.requestURI).thenReturn("/api/test")

                val stringWriter = StringWriter()
                val response = mock<HttpServletResponse>()
                whenever(response.writer).thenReturn(PrintWriter(stringWriter))

                // --- Act ---
                val result = interceptor.preHandle(request, response, handlerMethod)

                // --- Assert ---
                result shouldBe false
                verify(response).status = AuthenticationErrors.TOKEN_MISSING.httpCode
                verify(response).contentType = "application/json"
                // parseAccessToken should never be called when token is missing
                verify(tokenVerifier, never()).verifyAccessToken(any())

                val body = stringWriter.toString()
                body.contains("Auth.Token.Missing") shouldBe true
                body.contains(AuthenticationErrors.TOKEN_MISSING.message) shouldBe true
            }
        }
    })
