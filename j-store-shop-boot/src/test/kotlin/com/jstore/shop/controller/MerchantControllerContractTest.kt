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
package com.jstore.shop.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.shop.domain.merchant.Merchant
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantMembership
import com.jstore.shop.domain.merchant.MerchantMembershipId
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.service.MerchantService
import com.jstore.shop.service.UserAccountLookup
import com.jstore.user.domain.useraccount.UserId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class MerchantControllerContractTest {
    private lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        var sequence = 100L
        val memberships = FakeMembershipRepository()
        val service =
            MerchantService(
                { ++sequence },
                FakeMerchantRepository(memberships),
                memberships,
                UserAccountLookup { it in setOf(10L, 20L) },
            )
        mvc =
            MockMvcBuilders.standaloneSetup(MerchantController(service))
                .setCustomArgumentResolvers(CurrentUserResolver())
                .build()
    }

    @Test
    fun `merchant HTTP contract is preserved after module split`() {
        mvc.perform(
                post("/api/merchants")
                    .header("X-Test-User", "10")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"name":"示例商户"}""")
            )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(101))
            .andExpect(jsonPath("$.name").value("示例商户"))

        mvc.perform(get("/api/merchants").header("X-Test-User", "10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].roles[0]").value("OWNER"))
    }

    private class CurrentUserResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentPrincipal::class.java) &&
                parameter.parameterType == AuthenticatedPrincipal::class.java

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: org.springframework.web.bind.support.WebDataBinderFactory?,
        ) =
            AuthenticatedPrincipal(
                "issuer-a",
                UserId(webRequest.getHeader("X-Test-User")!!.toLong()),
            )
    }

    private class FakeMerchantRepository(private val memberships: MerchantMembershipRepository) :
        MerchantRepository {
        private val values = linkedMapOf<MerchantId, Merchant>()

        override fun createWithOwner(merchant: Merchant, ownerMembership: MerchantMembership) =
            merchant.also {
                values[it.id] = it
                memberships.save(ownerMembership)
            }

        override fun save(aggregate: Merchant) = aggregate.also { values[it.id] = it }

        override fun findById(id: MerchantId) = values[id]
    }

    private class FakeMembershipRepository : MerchantMembershipRepository {
        private val values = linkedMapOf<MerchantMembershipId, MerchantMembership>()

        override fun save(aggregate: MerchantMembership) = aggregate.also { values[it.id] = it }

        override fun findById(id: MerchantMembershipId) = values[id]

        override fun findByMerchantAndUser(merchantId: MerchantId, userId: Long) =
            values.values.firstOrNull { it.merchantId == merchantId && it.userId == userId }

        override fun findByUser(userId: Long) = values.values.filter { it.userId == userId }
    }
}
