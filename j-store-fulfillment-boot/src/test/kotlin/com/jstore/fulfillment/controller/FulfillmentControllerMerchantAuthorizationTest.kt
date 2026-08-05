package com.jstore.fulfillment.controller

import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.FulfillmentOrderId
import com.jstore.fulfillment.domain.FulfillmentOrderImpl
import com.jstore.fulfillment.domain.ShippingRecipient
import com.jstore.fulfillment.service.FulfillmentUseCase
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

class FulfillmentControllerMerchantAuthorizationTest {
    @Test
    fun `order manager can access fulfillment while numerically equal non-member cannot`() {
        val service = mock(FulfillmentUseCase::class.java)
        val order =
            FulfillmentOrderImpl(
                FulfillmentOrderId(1),
                orderId = 9,
                merchantId = 70,
                recipient = ShippingRecipient("张三", null, null, "CN", "310000", null),
                items = listOf(FulfillmentItem(1, 2, 1)),
            )
        `when`(service.getByOrderId(9)).thenReturn(Success(order))

        val memberships = FakeMembershipRepository()
        val merchants =
            FakeMerchantRepository(memberships).also {
                it.save(Merchant(MerchantId(70), "示例商户"))
            }
        memberships.save(
            MerchantMembership(
                MerchantMembershipId(1),
                MerchantId(70),
                900,
                setOf(MerchantRole.ORDER_MANAGER),
            )
        )
        val controller =
            FulfillmentController(service, MerchantAuthorizationService(merchants, memberships))

        assertEquals(HttpStatus.OK, controller.get(UserId(900), 9).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.get(UserId(70), 9).statusCode)
    }

    private class FakeMerchantRepository(private val memberships: MerchantMembershipRepository) :
        MerchantRepository {
        private val values = mutableMapOf<MerchantId, Merchant>()

        override fun createWithOwner(
            merchant: Merchant,
            ownerMembership: MerchantMembership,
        ): Merchant {
            memberships.save(ownerMembership)
            return save(merchant)
        }

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
