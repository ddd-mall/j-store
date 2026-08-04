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
        assertTrue(orderManager.allows(MerchantPermission.FULFILLMENT_MANAGE))
        assertFalse(orderManager.allows(MerchantPermission.PAYMENT_MANAGE))
        assertFalse(orderManager.allows(MerchantPermission.MEMBER_MANAGE))

        assertTrue(finance.allows(MerchantPermission.PAYMENT_READ))
        assertTrue(finance.allows(MerchantPermission.PAYMENT_MANAGE))
        assertFalse(finance.allows(MerchantPermission.FULFILLMENT_MANAGE))
        assertFalse(finance.allows(MerchantPermission.MEMBER_MANAGE))
    }

    @Test
    fun `disabled membership grants no permission`() {
        val membership = membership(roles = setOf(MerchantRole.ADMIN))

        assertIs<Success<Unit>>(membership.disable())

        MerchantPermission.entries.forEach { assertFalse(membership.allows(it), it.name) }
    }

    @Test
    fun `owner cannot be changed or disabled through ordinary member operations`() {
        val membership = membership(roles = setOf(MerchantRole.OWNER))

        val roleResult = membership.changeRoles(setOf(MerchantRole.ADMIN))
        val disableResult = membership.disable()

        assertEquals(MerchantErrors.OWNER_PROTECTED, assertIs<Failure<*>>(roleResult).error)
        assertEquals(MerchantErrors.OWNER_PROTECTED, assertIs<Failure<*>>(disableResult).error)
        assertTrue(membership.allows(MerchantPermission.MEMBER_MANAGE))
    }

    @Test
    fun `non-owner roles can be changed but owner role cannot be granted`() {
        val membership = membership(roles = setOf(MerchantRole.VIEWER))

        assertIs<Success<Unit>>(
            membership.changeRoles(setOf(MerchantRole.ORDER_MANAGER, MerchantRole.FINANCE))
        )
        assertTrue(membership.allows(MerchantPermission.FULFILLMENT_MANAGE))
        assertTrue(membership.allows(MerchantPermission.PAYMENT_MANAGE))

        val result = membership.changeRoles(setOf(MerchantRole.OWNER))
        assertEquals(MerchantErrors.OWNER_ROLE_RESERVED, assertIs<Failure<*>>(result).error)
    }

    private fun membership(
        id: Long = 1,
        roles: Set<MerchantRole>,
    ) =
        MerchantMembership(
            id = MerchantMembershipId(id),
            merchantId = MerchantId(11),
            userId = 101,
            roles = roles,
            status = MerchantMembershipStatus.ACTIVE,
        )
}
