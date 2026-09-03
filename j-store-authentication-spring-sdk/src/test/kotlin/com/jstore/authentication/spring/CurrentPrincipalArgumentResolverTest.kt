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

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.context.AuthenticatedPrincipalContext
import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.MethodParameter
import org.springframework.web.context.request.NativeWebRequest

class CurrentPrincipalArgumentResolverTest :
    FunSpec({
        val resolver = CurrentPrincipalArgumentResolver()

        afterEach { AuthenticatedPrincipalContext.clear() }

        test("supports only CurrentPrincipal parameters") {
            val parameter = mock<MethodParameter>()
            whenever(parameter.hasParameterAnnotation(CurrentPrincipal::class.java))
                .thenReturn(true)
            whenever(parameter.parameterType).thenReturn(AuthenticatedPrincipal::class.java)

            resolver.supportsParameter(parameter) shouldBe true
        }

        test("resolves the complete scoped principal") {
            val principal = AuthenticatedPrincipal("issuer-a", AuthenticatedAccountId(123))
            AuthenticatedPrincipalContext.set(principal)
            val parameter = mock<MethodParameter>()
            whenever(parameter.isOptional).thenReturn(false)

            resolver.resolveArgument(parameter, null, mock<NativeWebRequest>(), null) shouldBe
                principal
        }

        test("returns null for an optional parameter without a principal") {
            val parameter = mock<MethodParameter>()
            whenever(parameter.isOptional).thenReturn(true)

            resolver.resolveArgument(parameter, null, mock<NativeWebRequest>(), null) shouldBe null
        }
    })
