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
package com.jstore.goods.domain.brand

import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.brand.persistence.BrandPO
import com.jstore.goods.domain.brand.persistence.BrandPOJpaRepository
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.content.LocalizedText
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class BrandRepositoryImpl(private val jpaRepository: BrandPOJpaRepository) : BrandRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: Brand): Brand {
        val po =
            jpaRepository.findById(aggregate.id.value).orElse(null)?.also { existing ->
                existing.name = JsonUtils.toJsonString(aggregate.name.values)
                existing.normalizedName = aggregate.normalizedName
                existing.status = aggregate.status
                existing.updatedAt = LocalDateTime.now()
            }
                ?: BrandPO(
                    id = aggregate.id.value,
                    merchantId = aggregate.merchantId.value,
                    name = JsonUtils.toJsonString(aggregate.name.values),
                    normalizedName = aggregate.normalizedName,
                    status = aggregate.status,
                )
        return Converter.toDomain(jpaRepository.save(po))
    }

    override fun findById(id: BrandId): Brand? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findByMerchantIdAndNormalizedName(
        merchantId: MerchantId,
        normalizedName: String,
    ): Brand? =
        jpaRepository
            .findByMerchantIdAndNormalizedName(merchantId.value, normalizedName)
            ?.let(Converter::toDomain)

    internal object Converter {
        fun toDomain(po: BrandPO): Brand =
            Brand(
                id = BrandId(po.id),
                merchantId = MerchantId(po.merchantId),
                name = LocalizedText(JsonUtils.deserialize(po.name)),
                status = po.status,
            )
    }
}
