package com.jstore.user.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.annotation.SkipLogin
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.Nickname
import com.jstore.user.domain.useraccount.Password
import com.jstore.user.domain.useraccount.UserAccountImpl
import com.jstore.user.domain.useraccount.UserAccountStatus
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.service.UserAccountUseCase
import kotlin.test.Test
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class UserAccountControllerContractTest {
    private val useCase = mock<UserAccountUseCase>()
    private val mvc =
        MockMvcBuilders.standaloneSetup(UserAccountController(useCase))
            .setCustomArgumentResolvers(CurrentUserResolver())
            .build()

    @Test
    fun `me lookup always uses authenticated user id`() {
        val account =
            UserAccountImpl(
                id = UserId(42),
                phoneNumber = PhoneNumber("+8613800138000"),
                nickname = Nickname("current-user"),
                passwordHash = Password("hash"),
                status = UserAccountStatus.ACTIVE,
            )
        whenever(useCase.findById(UserId(42))).thenReturn(Success(account))

        mvc.perform(get("/api/users/me").header("X-Test-User", "42"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.nickname").value("current-user"))

        verify(useCase).findById(UserId(42))
    }

    @Test
    fun `arbitrary user and consumer admin routes do not exist`() {
        listOf(
                get("/api/users/99"),
                post("/api/users/99/disable"),
                post("/api/users/99/enable"),
                post("/api/users/99/force-offline"),
            )
            .forEach { request -> mvc.perform(request).andExpect(status().isNotFound) }
    }

    @Test
    fun `controller defaults to authentication and only onboarding endpoints skip login`() {
        assertTrue(UserAccountController::class.java.isAnnotationPresent(RequireLogin::class.java))
        listOf(
                "requestPhoneVerification",
                "register",
                "login",
                "refreshToken",
            )
            .forEach { name ->
                val method = UserAccountController::class.java.methods.single { it.name == name }
                assertTrue(method.isAnnotationPresent(SkipLogin::class.java), name)
            }
    }

    private class CurrentUserResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentUserId::class.java) &&
                parameter.parameterType == UserId::class.java

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: org.springframework.web.bind.support.WebDataBinderFactory?,
        ) = UserId(webRequest.getHeader("X-Test-User")!!.toLong())
    }
}
