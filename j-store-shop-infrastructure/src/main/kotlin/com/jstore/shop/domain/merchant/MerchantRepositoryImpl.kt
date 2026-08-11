/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.shop.domain.merchant

import com.jstore.shop.domain.merchant.persistence.MerchantMembershipPOJpaRepository
import com.jstore.shop.domain.merchant.persistence.MerchantPO
import com.jstore.shop.domain.merchant.persistence.MerchantPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class MerchantRepositoryImpl(
    private val jpaRepository: MerchantPOJpaRepository,
    private val membershipJpaRepository: MerchantMembershipPOJpaRepository,
) : MerchantRepository {
    @Transactional(propagation = Propagation.MANDATORY)
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

    @Transactional(propagation = Propagation.MANDATORY)
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
