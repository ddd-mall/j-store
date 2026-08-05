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
package com.jstore.goods.domain.commodity

import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.persistence.SkuPO
import com.jstore.goods.domain.commodity.persistence.SpuPO
import com.jstore.goods.domain.commodity.persistence.SpuPOJpaRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class SpuRepositoryImpl(private val jpaRepository: SpuPOJpaRepository) : SpuRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: Spu): Spu {
        val po = Converter.toPO(entity)
        po.updateTime = LocalDateTime.now()
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: SpuId): Spu? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findDraftBySourceSpuId(sourceSpuId: SpuId): Spu? {
        return jpaRepository
            .findBySourceSpuIdAndStatus(
                sourceSpuId.value,
                CommodityStatus.DRAFT,
            )
            ?.let { Converter.toDomain(it) }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun delete(spu: Spu) {
        jpaRepository.deleteById(spu.id.value)
    }

    private object Converter {

        fun toPO(spu: Spu): SpuPO {
            return SpuPO(
                id = spu.id.value,
                merchantId = spu.merchantId.value,
                name = spu.name,
                description = spu.description,
                status = spu.status,
                version = spu.version,
                sourceSpuId = spu.sourceSpuId?.value,
                skus = spu.skus.map { toSkuPO(it, spu.id.value) }.toMutableList(),
            )
        }

        fun toSkuPO(sku: Sku, spuId: Long): SkuPO {
            return SkuPO(
                id = sku.id.value,
                spuId = spuId,
                skuName = sku.skuName,
                attributes = JsonUtils.toJsonString(sku.attributes),
                price = sku.price.toBigDecimal(),
                merchantCode = sku.merchantCode,
                barcode = sku.barcode,
            )
        }

        fun toDomain(po: SpuPO): Spu {
            return SpuImpl(
                id = SpuId(po.id),
                merchantId = MerchantId(po.merchantId),
                name = po.name,
                description = po.description,
                _status = po.status,
                _skus = po.skus.map { toDomainSku(it) }.toMutableList(),
                _version = po.version,
                sourceSpuId = po.sourceSpuId?.let { SpuId(it) },
            )
        }

        fun toDomainSku(po: SkuPO): Sku {
            val attrs: List<Attribute<String, String>> = JsonUtils.deserialize(po.attributes)
            return SkuImpl(
                id = SkuId(po.id),
                skuName = po.skuName,
                attributes = attrs,
                price = Price.fromBigDecimal(po.price),
                merchantCode = po.merchantCode,
                barcode = po.barcode,
            )
        }
    }
}
