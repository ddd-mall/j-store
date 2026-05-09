package com.jstore.authentication.spring

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.context.AuthenticatedUserContext
import com.jstore.user.domain.useraccount.UserId
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class CurrentUserIdArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean {
        return parameter.hasParameterAnnotation(CurrentUserId::class.java)
                && parameter.parameterType == UserId::class.java
    }

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): UserId? {
        if (parameter.isOptional) {
            return AuthenticatedUserContext.getCurrentUserIdOrNull()
        }
        return AuthenticatedUserContext.getCurrentUserId()
    }
}
