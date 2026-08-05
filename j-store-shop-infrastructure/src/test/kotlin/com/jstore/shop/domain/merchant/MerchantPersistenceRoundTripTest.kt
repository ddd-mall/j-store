package com.jstore.shop.domain.merchant

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MerchantPersistenceRoundTripTest {
    @Test
    fun `merchant round trip preserves identity state and timestamps`() {
        val created = LocalDateTime.of(2026, 8, 4, 10, 0)
        val updated = created.plusHours(2)
        val merchant = Merchant(MerchantId(11), "示例商户", MerchantStatus.DISABLED, created, updated)

        val restored =
            MerchantRepositoryImpl.Converter.toDomain(
                MerchantRepositoryImpl.Converter.toPO(merchant)
            )

        assertEquals(merchant.id, restored.id)
        assertEquals(merchant.name, restored.name)
        assertEquals(merchant.status, restored.status)
        assertEquals(merchant.createTime, restored.createTime)
        assertEquals(merchant.updateTime, restored.updateTime)
    }

    @Test
    fun `membership round trip preserves all scoped roles`() {
        val membership =
            MerchantMembership(
                MerchantMembershipId(21),
                MerchantId(11),
                102,
                setOf(MerchantRole.ORDER_MANAGER, MerchantRole.FINANCE),
                MerchantMembershipStatus.DISABLED,
            )

        val restored =
            MerchantMembershipRepositoryImpl.Converter.toDomain(
                MerchantMembershipRepositoryImpl.Converter.toPO(membership)
            )

        assertEquals(membership.id, restored.id)
        assertEquals(membership.merchantId, restored.merchantId)
        assertEquals(membership.userId, restored.userId)
        assertEquals(membership.roles, restored.roles)
        assertEquals(membership.status, restored.status)
    }
}
