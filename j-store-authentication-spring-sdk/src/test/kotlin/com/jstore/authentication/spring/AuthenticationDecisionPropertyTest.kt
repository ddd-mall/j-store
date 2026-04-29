package com.jstore.authentication.spring

import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.annotation.SkipLogin
import com.jstore.authentication.config.AuthenticationConfigurer
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import jakarta.servlet.http.HttpServletRequest
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.util.AntPathMatcher
import io.kotest.common.ExperimentalKotest
import org.springframework.web.method.HandlerMethod

// Feature: authentication-sdk, Property 1: 统一认证判定
@OptIn(ExperimentalKotest::class)
class AuthenticationDecisionPropertyTest : FunSpec({

    // **Validates: Requirements 1.2, 1.3, 1.4, 1.5, 2.2, 2.3, 2.4, 2.5, 3.2, 3.3**
    test("requiresAuthentication follows priority rules for any annotation + path combination") {
        val pathSegments = listOf("api", "public", "admin", "users", "orders", "health", "login", "v1", "v2")

        // Generator for realistic URL paths like "/api/users", "/admin/orders"
        val pathArb = Arb.list(Arb.element(pathSegments), range = 1..3)
            .map { segments -> "/" + segments.joinToString("/") }

        // Generator for path patterns (may include wildcards)
        val patternSegments = listOf("api", "public", "admin", "users", "orders", "health", "login", "**", "*")
        val patternArb = Arb.list(Arb.element(patternSegments), range = 1..3)
            .map { segments -> "/" + segments.joinToString("/") }

        val patternListArb = Arb.list(patternArb, range = 0..3)

        val pathMatcher = AntPathMatcher()

        checkAll(
            PropTestConfig(iterations = 200),
            Arb.boolean(),       // hasSkipLogin
            Arb.boolean(),       // hasMethodRequireLogin
            Arb.boolean(),       // hasClassRequireLogin
            pathArb,             // requestPath
            patternListArb,      // authenticatedPatterns
            patternListArb,      // excludedPatterns
        ) { hasSkipLogin, hasMethodRequireLogin, hasClassRequireLogin,
            requestPath, authenticatedPatterns, excludedPatterns ->

            // --- Build mocks ---
            val handlerMethod = mock(HandlerMethod::class.java)
            `when`(handlerMethod.hasMethodAnnotation(SkipLogin::class.java)).thenReturn(hasSkipLogin)
            `when`(handlerMethod.hasMethodAnnotation(RequireLogin::class.java)).thenReturn(hasMethodRequireLogin)

            val beanType: Class<*> = if (hasClassRequireLogin) AnnotatedController::class.java else UnannotatedController::class.java
            @Suppress("UNCHECKED_CAST")
            `when`(handlerMethod.beanType).thenReturn(beanType as Class<*>)

            val request = mock(HttpServletRequest::class.java)
            `when`(request.requestURI).thenReturn(requestPath)

            val configurer = object : AuthenticationConfigurer {
                override fun authenticatedPathPatterns(): List<String> = authenticatedPatterns
                override fun excludedPathPatterns(): List<String> = excludedPatterns
            }

            val interceptor = AuthenticationInterceptor(
                tokenProvider = mock(TokenProvider::class.java),
                tokenStore = mock(TokenStore::class.java),
                configurers = listOf(configurer),
            )

            // --- Compute expected result using the same priority rules ---
            val matchesExcluded = excludedPatterns.any { pattern -> pathMatcher.match(pattern, requestPath) }
            val matchesAuthenticated = authenticatedPatterns.any { pattern -> pathMatcher.match(pattern, requestPath) }

            val expected = when {
                hasSkipLogin -> false                                          // Priority 1
                hasMethodRequireLogin || hasClassRequireLogin -> true          // Priority 2
                matchesExcluded -> false                                       // Priority 3
                matchesAuthenticated -> true                                   // Priority 4
                else -> false                                                  // Priority 5
            }

            // --- Assert ---
            val actual = interceptor.requiresAuthentication(handlerMethod, request)
            actual shouldBe expected
        }
    }
})

@RequireLogin
class AnnotatedController

class UnannotatedController
