package com.jstore.shop.domain.merchant

import com.jstore.common.framework.Repository

interface MerchantRepository : Repository<MerchantId, Merchant> {
    /** 原子创建商户及其 OWNER 成员关系。 */
    fun createWithOwner(merchant: Merchant, ownerMembership: MerchantMembership): Merchant
}

interface MerchantMembershipRepository : Repository<MerchantMembershipId, MerchantMembership> {
    fun findByMerchantAndUser(merchantId: MerchantId, userId: Long): MerchantMembership?

    fun findByUser(userId: Long): List<MerchantMembership>
}
