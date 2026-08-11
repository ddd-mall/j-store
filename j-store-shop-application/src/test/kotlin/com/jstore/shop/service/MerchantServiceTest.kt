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
package com.jstore.shop.service

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.shop.domain.merchant.Merchant
import com.jstore.shop.domain.merchant.MerchantErrors
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantMembership
import com.jstore.shop.domain.merchant.MerchantMembershipId
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantPermission
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.domain.merchant.MerchantRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MerchantServiceTest {
    private val memberships = FakeMembershipRepository()
    private val merchants = FakeMerchantRepository(memberships)
    private var sequence = 100L
    private val service =
        MerchantService(
            { ++sequence },
            merchants,
            memberships,
            UserAccountLookup { it in setOf(10L, 20L) },
        )
    private val authorization = MerchantAuthorizationService(merchants, memberships)

    @Test
    fun `creating merchant makes creator its protected owner`() {
        val merchant = assertIs<Success<Merchant>>(service.create(10, "示例商户")).value

        assertEquals(
            setOf(MerchantRole.OWNER),
            memberships.findByMerchantAndUser(merchant.id, 10)?.roles,
        )
        assertTrue(authorization.hasPermission(10, merchant.id, MerchantPermission.MEMBER_MANAGE))
    }

    @Test
    fun `member management remains scoped to authorized existing accounts`() {
        val merchant = assertIs<Success<Merchant>>(service.create(10, "示例商户")).value
        assertIs<Success<MerchantMembership>>(
            service.addMember(10, merchant.id, 20, setOf(MerchantRole.VIEWER))
        )

        val forbidden = service.addMember(20, merchant.id, 10, setOf(MerchantRole.FINANCE))
        val missing = service.addMember(10, merchant.id, 999, setOf(MerchantRole.FINANCE))

        assertEquals(MerchantErrors.FORBIDDEN, assertIs<Failure<*>>(forbidden).error)
        assertEquals(MerchantErrors.USER_NOT_FOUND, assertIs<Failure<*>>(missing).error)
        assertFalse(authorization.hasPermission(20, merchant.id, MerchantPermission.MEMBER_MANAGE))
    }

    private class FakeMerchantRepository(private val memberships: MerchantMembershipRepository) :
        MerchantRepository {
        private val values = linkedMapOf<MerchantId, Merchant>()

        override fun createWithOwner(
            merchant: Merchant,
            ownerMembership: MerchantMembership,
        ): Merchant = merchant.also {
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
