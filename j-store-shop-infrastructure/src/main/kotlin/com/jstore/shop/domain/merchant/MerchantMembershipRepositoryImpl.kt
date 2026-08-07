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

import com.jstore.shop.domain.merchant.persistence.MerchantMembershipPO
import com.jstore.shop.domain.merchant.persistence.MerchantMembershipPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class MerchantMembershipRepositoryImpl(
    private val jpaRepository: MerchantMembershipPOJpaRepository
) : MerchantMembershipRepository {
    @Transactional
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
