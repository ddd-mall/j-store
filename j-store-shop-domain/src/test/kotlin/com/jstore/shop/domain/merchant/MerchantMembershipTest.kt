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
