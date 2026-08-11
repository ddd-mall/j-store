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
package com.jstore.payment.controller

import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.PaymentOrderId
import com.jstore.payment.domain.payment.PaymentOrderImpl
import com.jstore.payment.service.PaymentUseCase
import com.jstore.shop.domain.merchant.Merchant
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantMembership
import com.jstore.shop.domain.merchant.MerchantMembershipId
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.domain.merchant.MerchantRole
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.user.domain.useraccount.UserId
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

class PaymentControllerMerchantAuthorizationTest {
    @Test
    fun `finance member can access payment while numerically equal non-member cannot`() {
        val service = mock(PaymentUseCase::class.java)
        val order = PaymentOrderImpl(PaymentOrderId(1), 9, 70, Price.ofFen(100), "CNY")
        `when`(service.getByOrderId(9)).thenReturn(Success(order))

        val merchants =
            FakeMerchantRepository().also {
                it.save(Merchant(MerchantId(70), "示例商户"))
            }
        val memberships =
            FakeMembershipRepository().also {
                it.save(
                    MerchantMembership(
                        MerchantMembershipId(1),
                        MerchantId(70),
                        900,
                        setOf(MerchantRole.FINANCE),
                    )
                )
            }
        val controller =
            PaymentController(service, MerchantAuthorizationService(merchants, memberships))

        assertEquals(HttpStatus.OK, controller.get(UserId(900), 9).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.get(UserId(70), 9).statusCode)
    }

    private class FakeMerchantRepository : MerchantRepository {
        private val values = mutableMapOf<MerchantId, Merchant>()

        override fun createWithOwner(merchant: Merchant, ownerMembership: MerchantMembership) =
            save(merchant)

        override fun save(entity: Merchant) = entity.also { values[it.id] = it }

        override fun findById(id: MerchantId) = values[id]
    }

    private class FakeMembershipRepository : MerchantMembershipRepository {
        private val values = mutableMapOf<MerchantMembershipId, MerchantMembership>()

        override fun save(entity: MerchantMembership) = entity.also { values[it.id] = it }

        override fun findById(id: MerchantMembershipId) = values[id]

        override fun findByMerchantAndUser(merchantId: MerchantId, userId: Long) =
            values.values.firstOrNull { it.merchantId == merchantId && it.userId == userId }

        override fun findByUser(userId: Long) = values.values.filter { it.userId == userId }
    }
}
