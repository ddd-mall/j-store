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
package com.jstore.shop.domain.merchant

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MerchantMembershipTest {
    @Test
    fun `roles grant only their merchant-scoped permissions`() {
        val orderManager = membership(roles = setOf(MerchantRole.ORDER_MANAGER))
        val finance = membership(id = 2, roles = setOf(MerchantRole.FINANCE))

        assertTrue(orderManager.allows(MerchantPermission.ORDER_READ))
        assertTrue(orderManager.allows(MerchantPermission.AFTER_SALE_MANAGE))
        assertFalse(orderManager.allows(MerchantPermission.PAYMENT_MANAGE))
        assertTrue(finance.allows(MerchantPermission.PAYMENT_MANAGE))
        assertFalse(finance.allows(MerchantPermission.FULFILLMENT_MANAGE))
    }

    @Test
    fun `owner cannot be changed or disabled through ordinary member operations`() {
        val membership = membership(roles = setOf(MerchantRole.OWNER))

        assertEquals(
            MerchantErrors.OWNER_PROTECTED,
            assertIs<Failure<*>>(membership.changeRoles(setOf(MerchantRole.ADMIN))).error,
        )
        assertEquals(
            MerchantErrors.OWNER_PROTECTED,
            assertIs<Failure<*>>(membership.disable()).error,
        )
    }

    @Test
    fun `disabled membership grants no permission`() {
        val membership = membership(roles = setOf(MerchantRole.ADMIN))
        assertIs<Success<Unit>>(membership.disable())
        MerchantPermission.entries.forEach { assertFalse(membership.allows(it), it.name) }
    }

    private fun membership(id: Long = 1, roles: Set<MerchantRole>) =
        MerchantMembership(
            MerchantMembershipId(id),
            MerchantId(11),
            101,
            roles,
        )
}
