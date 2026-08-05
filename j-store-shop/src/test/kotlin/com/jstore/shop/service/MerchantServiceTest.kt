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
    private val membershipRepository = FakeMembershipRepository()
    private val merchantRepository = FakeMerchantRepository(membershipRepository)
    private val existingUsers = mutableSetOf(10L, 20L, 30L)
    private var sequence = 100L
    private val service =
        MerchantService(
            idGenerator = { ++sequence },
            merchantRepository = merchantRepository,
            membershipRepository = membershipRepository,
            userAccountLookup = UserAccountLookup { it in existingUsers },
        )
    private val authorization =
        MerchantAuthorizationService(merchantRepository, membershipRepository)

    @Test
    fun `creating merchant makes creator its protected owner`() {
        val result = service.create(creatorUserId = 10, name = "示例商户")

        val merchant = assertIs<Success<Merchant>>(result).value
        val owner = membershipRepository.findByMerchantAndUser(merchant.id, 10)!!
        assertEquals(setOf(MerchantRole.OWNER), owner.roles)
        assertTrue(authorization.hasPermission(10, merchant.id, MerchantPermission.MEMBER_MANAGE))
    }

    @Test
    fun `member permissions are isolated by merchant`() {
        val first = assertIs<Success<Merchant>>(service.create(10, "一店")).value
        val second = assertIs<Success<Merchant>>(service.create(20, "二店")).value
        assertIs<Success<MerchantMembership>>(
            service.addMember(10, first.id, 30, setOf(MerchantRole.ORDER_MANAGER))
        )

        assertTrue(authorization.hasPermission(30, first.id, MerchantPermission.FULFILLMENT_MANAGE))
        assertFalse(
            authorization.hasPermission(30, second.id, MerchantPermission.FULFILLMENT_MANAGE)
        )
    }

    @Test
    fun `only member managers can add members and target account must exist`() {
        val merchant = assertIs<Success<Merchant>>(service.create(10, "示例商户")).value
        assertIs<Success<MerchantMembership>>(
            service.addMember(10, merchant.id, 20, setOf(MerchantRole.VIEWER))
        )

        val forbidden = service.addMember(20, merchant.id, 30, setOf(MerchantRole.FINANCE))
        val missingUser = service.addMember(10, merchant.id, 999, setOf(MerchantRole.FINANCE))

        assertEquals(MerchantErrors.FORBIDDEN, assertIs<Failure<*>>(forbidden).error)
        assertEquals(MerchantErrors.USER_NOT_FOUND, assertIs<Failure<*>>(missingUser).error)
    }

    @Test
    fun `disabled merchant denies every member permission`() {
        val merchant = assertIs<Success<Merchant>>(service.create(10, "示例商户")).value
        merchant.disable()
        merchantRepository.save(merchant)

        assertFalse(authorization.hasPermission(10, merchant.id, MerchantPermission.MEMBER_MANAGE))
    }

    private class FakeMerchantRepository(
        private val membershipRepository: MerchantMembershipRepository
    ) : MerchantRepository {
        private val values = linkedMapOf<MerchantId, Merchant>()

        override fun createWithOwner(
            merchant: Merchant,
            ownerMembership: MerchantMembership,
        ): Merchant {
            values[merchant.id] = merchant
            membershipRepository.save(ownerMembership)
            return merchant
        }

        override fun save(entity: Merchant): Merchant = entity.also { values[it.id] = it }

        override fun findById(id: MerchantId): Merchant? = values[id]
    }

    private class FakeMembershipRepository : MerchantMembershipRepository {
        private val values = linkedMapOf<MerchantMembershipId, MerchantMembership>()

        override fun save(entity: MerchantMembership): MerchantMembership = entity.also {
            values[it.id] = it
        }

        override fun findById(id: MerchantMembershipId): MerchantMembership? = values[id]

        override fun findByMerchantAndUser(
            merchantId: MerchantId,
            userId: Long,
        ): MerchantMembership? =
            values.values.firstOrNull { it.merchantId == merchantId && it.userId == userId }

        override fun findByUser(userId: Long): List<MerchantMembership> =
            values.values.filter { it.userId == userId }
    }
}
