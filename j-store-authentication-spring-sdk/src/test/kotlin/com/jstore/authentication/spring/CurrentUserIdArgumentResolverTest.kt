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

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.context.AuthenticatedUserContext
import com.jstore.user.domain.useraccount.UserId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter

class CurrentUserIdArgumentResolverTest :
    FunSpec({
        val resolver = CurrentUserIdArgumentResolver()

        afterEach {
            AuthenticatedUserContext.clear()
        }

        // _需求: 5.6_ — supportsParameter returns true when @CurrentUserId + UserId type
        test(
            "supportsParameter returns true when parameter has @CurrentUserId and type is UserId"
        ) {
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

        test(
            "supportsParameter returns false when parameter has @CurrentUserId but type is String"
        ) {
            val parameter = mock<MethodParameter>()
            whenever(parameter.hasParameterAnnotation(CurrentUserId::class.java)).thenReturn(true)
            whenever(parameter.parameterType).thenReturn(String::class.java)

            resolver.supportsParameter(parameter) shouldBe false
        }

        // _需求: 5.6_ — supportsParameter returns false when annotation is missing
        test(
            "supportsParameter returns false when parameter type is UserId but missing @CurrentUserId"
        ) {
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
            whenever(parameter.isOptional).thenReturn(false)
            val webRequest = mock<org.springframework.web.context.request.NativeWebRequest>()

            val result = resolver.resolveArgument(parameter, null, webRequest, null)

            result shouldBe userId
        }

        test("resolveArgument returns null when parameter is nullable and no user in context") {
            val parameter = mock<MethodParameter>()
            whenever(parameter.isOptional).thenReturn(true)
            val webRequest = mock<org.springframework.web.context.request.NativeWebRequest>()

            val result = resolver.resolveArgument(parameter, null, webRequest, null)

            result shouldBe null
        }

        test(
            "resolveArgument returns UserId when parameter is nullable and user exists in context"
        ) {
            val userId = UserId(456L)
            AuthenticatedUserContext.set(userId)

            val parameter = mock<MethodParameter>()
            whenever(parameter.isOptional).thenReturn(true)
            val webRequest = mock<org.springframework.web.context.request.NativeWebRequest>()

            val result = resolver.resolveArgument(parameter, null, webRequest, null)

            result shouldBe userId
        }
    })
