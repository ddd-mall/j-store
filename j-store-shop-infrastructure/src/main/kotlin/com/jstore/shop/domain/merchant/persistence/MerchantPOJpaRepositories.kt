package com.jstore.shop.domain.merchant.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface MerchantPOJpaRepository : JpaRepository<MerchantPO, Long>

interface MerchantMembershipPOJpaRepository : JpaRepository<MerchantMembershipPO, Long> {
    fun findByMerchantIdAndUserId(merchantId: Long, userId: Long): MerchantMembershipPO?

    fun findAllByUserIdOrderByMerchantIdAsc(userId: Long): List<MerchantMembershipPO>
}
