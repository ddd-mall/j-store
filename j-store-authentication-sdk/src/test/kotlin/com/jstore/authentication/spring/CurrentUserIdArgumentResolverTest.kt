package com.jstore.authentication.spring

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.context.AuthenticatedUserContext
import com.jstore.user.domain.useraccount.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter

class CurrentUserIdArgumentResolverTest : FunSpec({

    val resolver = CurrentUserIdArgumentResolver()

    afterEach {
        AuthenticatedUserContext.clear()
    }

    // _需求: 5.6_ — supportsParameter returns true when @CurrentUserId + UserId type
    test("supportsParameter returns true when parameter has @CurrentUserId and type is UserId") {
        val parameter = mock<MethodParameter>()
        whenever(parameter.hasParameterAnnotation(CurrentUserId::class.java)).thenReturn(true)
        whenever(parameter.parameterType).thenReturn(UserId::class.java)

        resolver.supportsParameter(parameter) shouldBe true
    }

    // _需求: 5.6_ — supportsParameter returns false when type is not UserId
    test("supportsParameter returns false when parameter has @CurrentUserId but type is Long") {
        val parameter = mock<MethodParameter>()
        whenever(parameter.hasParameterAnnotation(CurrentUserId::class.java)).thenReturn(true)
        whenever(parameter.parameterType).thenReturn(Long::class.java)

        resolver.supportsParameter(parameter) shouldBe false
    }

    test("supportsParameter returns false when parameter has @CurrentUserId but type is String") {
        val parameter = mock<MethodParameter>()
        whenever(parameter.hasParameterAnnotation(CurrentUserId::class.java)).thenReturn(true)
        whenever(parameter.parameterType).thenReturn(String::class.java)

        resolver.supportsParameter(parameter) shouldBe false
    }

    // _需求: 5.6_ — supportsParameter returns false when annotation is missing
    test("supportsParameter returns false when parameter type is UserId but missing @CurrentUserId") {
        val parameter = mock<MethodParameter>()
        whenever(parameter.hasParameterAnnotation(CurrentUserId::class.java)).thenReturn(false)
        whenever(parameter.parameterType).thenReturn(UserId::class.java)

        resolver.supportsParameter(parameter) shouldBe false
    }

    // _需求: 5.6_ — resolveArgument returns UserId from AuthenticatedUserContext
    test("resolveArgument returns UserId from AuthenticatedUserContext") {
        val userId = UserId(123L)
        AuthenticatedUserContext.set(userId)

        val parameter = mock<MethodParameter>()
        val webRequest = mock<org.springframework.web.context.request.NativeWebRequest>()

        val result = resolver.resolveArgument(parameter, null, webRequest, null)

        result shouldBe userId
    }
})
