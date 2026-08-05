package com.jstore.shop.domain.merchant

import com.jstore.shop.domain.merchant.persistence.MerchantMembershipPOJpaRepository
import com.jstore.shop.domain.merchant.persistence.MerchantPO
import com.jstore.shop.domain.merchant.persistence.MerchantPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class MerchantRepositoryImpl(
    private val jpaRepository: MerchantPOJpaRepository,
    private val membershipJpaRepository: MerchantMembershipPOJpaRepository,
) : MerchantRepository {
    @Transactional
    override fun createWithOwner(
        merchant: Merchant,
        ownerMembership: MerchantMembership,
    ): Merchant {
        val saved = jpaRepository.save(Converter.toPO(merchant))
        membershipJpaRepository.save(
            MerchantMembershipRepositoryImpl.Converter.toPO(ownerMembership)
        )
        return Converter.toDomain(saved)
    }

    @Transactional
    override fun save(entity: Merchant): Merchant =
        Converter.toDomain(jpaRepository.save(Converter.toPO(entity)))

    @Transactional(readOnly = true)
    override fun findById(id: MerchantId): Merchant? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    internal object Converter {
        fun toPO(merchant: Merchant) =
            MerchantPO(
                id = merchant.id.value,
                name = merchant.name,
                status = merchant.status,
                createTime = merchant.createTime,
                updateTime = merchant.updateTime,
            )

        fun toDomain(po: MerchantPO) =
            Merchant(
                id = MerchantId(po.id),
                name = po.name,
                status = po.status,
                createTime = po.createTime,
                updateTime = po.updateTime,
            )
    }
}
