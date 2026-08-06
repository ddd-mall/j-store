package com.jstore.authentication.spring

import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.config.AuthenticationConfigurer
import com.jstore.authentication.error.AuthenticationErrors
import com.jstore.user.domain.useraccount.AuthTokenClaims
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
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
            val tokenProvider = mock<TokenProvider>()
            val tokenStore = mock<TokenStore>()
            val interceptor =
                AuthenticationInterceptor(
                    tokenProvider = tokenProvider,
                    tokenStore = tokenStore,
                    configurers = emptyList(),
                )

            val handlerMethod = mock<HandlerMethod>()
            whenever(handlerMethod.hasMethodAnnotation(RequireLogin::class.java)).thenReturn(true)

            val request = mock<HttpServletRequest>()
            whenever(request.getHeader("Authorization")).thenReturn("Bearer sometoken")
            whenever(request.requestURI).thenReturn("/api/test")

            // parseAccessToken throws an unexpected RuntimeException
            whenever(tokenProvider.parseAccessToken("sometoken"))
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
            val tokenProvider = mock<TokenProvider>()
            val tokenStore = mock<TokenStore>()

            // Configure a path pattern that also matches the request
            val configurer =
                object : AuthenticationConfigurer {
                    override fun authenticatedPathPatterns(): List<String> = listOf("/api/**")

                    override fun excludedPathPatterns(): List<String> = emptyList()
                }

            val interceptor =
                AuthenticationInterceptor(
                    tokenProvider = tokenProvider,
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
            val claims = AuthTokenClaims(UserId(42L), "session-1", 2L, "jti-123")
            whenever(tokenProvider.parseAccessToken("validtoken")).thenReturn(claims)
            whenever(tokenStore.isSessionActive(UserId(42L), "session-1", 2L)).thenReturn(true)

            val response = mock<HttpServletResponse>()

            val result = interceptor.preHandle(request, response, handlerMethod)

            result shouldBe true

            // parseAccessToken should be called exactly once — not twice for annotation + path
            verify(tokenProvider, times(1)).parseAccessToken("validtoken")
        }
    })
