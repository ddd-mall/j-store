package com.jstore.authentication.spring

import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.error.AuthenticationErrors
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.io.PrintWriter
import java.io.StringWriter
import org.mockito.kotlin.*
import org.springframework.web.method.HandlerMethod

// Feature: authentication-sdk, Property 3: Token 验证错误映射
@OptIn(ExperimentalKotest::class)
class TokenValidationErrorPropertyTest :
    FunSpec({

        // **Validates: Requirements 4.4, 4.6, 4.7**

        test("parseAccessToken returns null produces Auth.Token.Invalid with HTTP 401") {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.string(minSize = 1, maxSize = 50).filter { !it.contains('\u0000') },
            ) { token ->
                val tokenProvider = mock<TokenProvider>()
                val tokenStore = mock<TokenStore>()
                val interceptor =
                    AuthenticationInterceptor(
                        tokenProvider = tokenProvider,
                        tokenStore = tokenStore,
                        configurers = emptyList(),
                    )

                val handlerMethod = mock<HandlerMethod>()
                whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java))
                    .thenReturn(true)

                val request = mock<HttpServletRequest>()
                whenever(request.getHeader("Authorization")).thenReturn("Bearer $token")
                whenever(request.requestURI).thenReturn("/api/test")

                // parseAccessToken returns null → token invalid
                whenever(tokenProvider.parseAccessToken(token)).thenReturn(null)

                val stringWriter = StringWriter()
                val response = mock<HttpServletResponse>()
                whenever(response.writer).thenReturn(PrintWriter(stringWriter))

                val result = interceptor.preHandle(request, response, handlerMethod)

                result shouldBe false
                verify(response).status = AuthenticationErrors.TOKEN_INVALID.httpCode
                AuthenticationErrors.TOKEN_INVALID.httpCode shouldBe 401
                verify(response).contentType = "application/json"

                val body = stringWriter.toString()
                body.contains("\"message\"") shouldBe true
                body.contains("\"errorCode\"") shouldBe true
                body.contains("Auth.Token.Invalid") shouldBe true
            }
        }

        test(
            "isAccessTokenBlacklisted returns true produces Auth.Token.Blacklisted with HTTP 401"
        ) {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.string(minSize = 1, maxSize = 50).filter { !it.contains('\u0000') },
                Arb.long(min = 1L, max = 100_000L),
                Arb.string(minSize = 1, maxSize = 30).filter { !it.contains('\u0000') },
            ) { token, userIdValue, jti ->
                val tokenProvider = mock<TokenProvider>()
                val tokenStore = mock<TokenStore>()
                val interceptor =
                    AuthenticationInterceptor(
                        tokenProvider = tokenProvider,
                        tokenStore = tokenStore,
                        configurers = emptyList(),
                    )

                val handlerMethod = mock<HandlerMethod>()
                whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java))
                    .thenReturn(true)

                val request = mock<HttpServletRequest>()
                whenever(request.getHeader("Authorization")).thenReturn("Bearer $token")
                whenever(request.requestURI).thenReturn("/api/test")

                // Token parses successfully but is blacklisted
                whenever(tokenProvider.parseAccessToken(token)).thenReturn(UserId(userIdValue))
                whenever(tokenProvider.getAccessTokenJti(token)).thenReturn(jti)
                whenever(tokenStore.isAccessTokenBlacklisted(jti)).thenReturn(true)

                val stringWriter = StringWriter()
                val response = mock<HttpServletResponse>()
                whenever(response.writer).thenReturn(PrintWriter(stringWriter))

                val result = interceptor.preHandle(request, response, handlerMethod)

                result shouldBe false
                verify(response).status = AuthenticationErrors.TOKEN_BLACKLISTED.httpCode
                AuthenticationErrors.TOKEN_BLACKLISTED.httpCode shouldBe 401
                verify(response).contentType = "application/json"

                val body = stringWriter.toString()
                body.contains("\"message\"") shouldBe true
                body.contains("\"errorCode\"") shouldBe true
                body.contains("Auth.Token.Blacklisted") shouldBe true
            }
        }

        test("missing Authorization header produces Auth.Token.Missing with HTTP 401") {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.string(minSize = 1, maxSize = 50),
            ) { requestPath ->
                val tokenProvider = mock<TokenProvider>()
                val tokenStore = mock<TokenStore>()
                val interceptor =
                    AuthenticationInterceptor(
                        tokenProvider = tokenProvider,
                        tokenStore = tokenStore,
                        configurers = emptyList(),
                    )

                val handlerMethod = mock<HandlerMethod>()
                whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java))
                    .thenReturn(true)

                val request = mock<HttpServletRequest>()
                // No Authorization header → token missing
                whenever(request.getHeader("Authorization")).thenReturn(null)
                whenever(request.requestURI).thenReturn("/api/$requestPath")

                val stringWriter = StringWriter()
                val response = mock<HttpServletResponse>()
                whenever(response.writer).thenReturn(PrintWriter(stringWriter))

                val result = interceptor.preHandle(request, response, handlerMethod)

                result shouldBe false
                verify(response).status = AuthenticationErrors.TOKEN_MISSING.httpCode
                AuthenticationErrors.TOKEN_MISSING.httpCode shouldBe 401
                verify(response).contentType = "application/json"

                val body = stringWriter.toString()
                body.contains("\"message\"") shouldBe true
                body.contains("\"errorCode\"") shouldBe true
                body.contains("Auth.Token.Missing") shouldBe true
            }
        }
    })
