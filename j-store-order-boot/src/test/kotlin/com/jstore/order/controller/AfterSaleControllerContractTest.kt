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
package com.jstore.order.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderId
import com.jstore.order.service.AfterSaleAccessUseCase
import com.jstore.order.service.AfterSaleUseCase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class AfterSaleControllerContractTest {
    private lateinit var service: AfterSaleUseCase
    private lateinit var access: AfterSaleAccessUseCase
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        service = mock(AfterSaleUseCase::class.java)
        access = mock(AfterSaleAccessUseCase::class.java)
        `when`(access.get("issuer-a", 41, AfterSaleId(9)))
            .thenReturn(Failure(AfterSaleErrors.NOT_FOUND))
        `when`(access.list("issuer-a", 41, OrderId(8))).thenReturn(Success(emptyList()))
        mvc =
            MockMvcBuilders.standaloneSetup(AfterSaleController(service, access))
                .setCustomArgumentResolvers(CurrentUserResolver())
                .build()
    }

    @Test
    fun `all six routes use authenticated current user and required idempotency key`() {
        mvc.perform(get("/api/after-sales/9").header("X-Test-User", "41"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.errorCode").value(AfterSaleErrors.NOT_FOUND.errorCode))
        verify(access).get("issuer-a", 41, AfterSaleId(9))
        mvc.perform(get("/api/after-sales").param("orderId", "8").header("X-Test-User", "41"))
            .andExpect(status().isOk)
            .andExpect(content().json("[]"))
        listOf(
                post("/api/after-sales")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"orderId":8,"category":"OTHER","description":"x","items":[{"orderItemId":1,"quantity":1,"amount":100}]}"""
                    ),
                post("/api/after-sales/9/approve"),
                post("/api/after-sales/9/reject")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"rejectionReason":"x"}"""),
                post("/api/after-sales/9/cancel"),
            )
            .forEach { request ->
                mvc.perform(request.header("X-Test-User", "41")).andExpect(status().isBadRequest)
            }
    }

    @Test
    fun `create validates nested positive quantity and amount before service invocation`() {
        mvc.perform(
                post("/api/after-sales")
                    .header("X-Test-User", "41")
                    .header("Idempotency-Key", "key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"orderId":8,"category":"OTHER","description":"x","items":[{"orderItemId":1,"quantity":0,"amount":0}]}"""
                    )
            )
            .andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `reject validates reason and route JSON contract`() {
        mvc.perform(
                post("/api/after-sales/9/reject")
                    .header("X-Test-User", "41")
                    .header("Idempotency-Key", "key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"rejectionReason":""}""")
            )
            .andExpect(status().isBadRequest)
        verifyNoInteractions(service)
    }

    @Test
    fun `merchant staff approval delegates identity-aware application use case`() {
        `when`(access.approve(900, AfterSaleId(9), "key"))
            .thenReturn(Failure(AfterSaleErrors.ILLEGAL_STATE))

        mvc.perform(
                post("/api/after-sales/9/approve")
                    .header("X-Test-User", "900")
                    .header("Idempotency-Key", "key")
            )
            .andExpect(status().isConflict)

        verify(access).approve(900, AfterSaleId(9), "key")
    }

    private class CurrentUserResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentPrincipal::class.java)

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: org.springframework.web.bind.support.WebDataBinderFactory?,
        ) =
            AuthenticatedPrincipal(
                "issuer-a",
                AuthenticatedAccountId(webRequest.getHeader("X-Test-User")!!.toLong()),
            )
    }
}
