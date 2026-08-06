package com.jstore.shop.domain.merchant

import com.jstore.common.framework.AggregateRepository

interface MerchantRepository : AggregateRepository<MerchantId, Merchant> {
    fun createWithOwner(merchant: Merchant, ownerMembership: MerchantMembership): Merchant
}

interface MerchantMembershipRepository :
    AggregateRepository<MerchantMembershipId, MerchantMembership> {
    fun findByMerchantAndUser(merchantId: MerchantId, userId: Long): MerchantMembership?

    fun findByUser(userId: Long): List<MerchantMembership>
}
