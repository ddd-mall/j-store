package com.jstore.shop.domain.merchant

import com.jstore.shop.domain.merchant.persistence.MerchantMembershipPO
import com.jstore.shop.domain.merchant.persistence.MerchantMembershipPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class MerchantMembershipRepositoryImpl(
    private val jpaRepository: MerchantMembershipPOJpaRepository
) : MerchantMembershipRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: MerchantMembership): MerchantMembership =
        Converter.toDomain(jpaRepository.save(Converter.toPO(entity)))

    @Transactional(readOnly = true)
    override fun findById(id: MerchantMembershipId): MerchantMembership? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    @Transactional(readOnly = true)
    override fun findByMerchantAndUser(merchantId: MerchantId, userId: Long): MerchantMembership? =
        jpaRepository.findByMerchantIdAndUserId(merchantId.value, userId)?.let(Converter::toDomain)

    @Transactional(readOnly = true)
    override fun findByUser(userId: Long): List<MerchantMembership> =
        jpaRepository.findAllByUserIdOrderByMerchantIdAsc(userId).map(Converter::toDomain)

    internal object Converter {
        fun toPO(membership: MerchantMembership) =
            MerchantMembershipPO(
                id = membership.id.value,
                merchantId = membership.merchantId.value,
                userId = membership.userId,
                roles = membership.roles.toMutableSet(),
                status = membership.status,
                createTime = membership.createTime,
                updateTime = membership.updateTime,
            )

        fun toDomain(po: MerchantMembershipPO) =
            MerchantMembership(
                id = MerchantMembershipId(po.id),
                merchantId = MerchantId(po.merchantId),
                userId = po.userId,
                roles = po.roles.toSet(),
                status = po.status,
                createTime = po.createTime,
                updateTime = po.updateTime,
            )
    }
}
