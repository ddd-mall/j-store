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

import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.category.CategoryId
import com.jstore.goods.domain.commodity.persistence.SkuPO
import com.jstore.goods.domain.commodity.persistence.SpuPO
import com.jstore.goods.domain.commodity.persistence.SpuPOJpaRepository
import com.jstore.goods.domain.content.LocalizedText
import com.jstore.goods.domain.producttype.ProductTypeId
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class SpuRepositoryImpl(private val jpaRepository: SpuPOJpaRepository) : SpuRepository {

    override fun findPublishedBySkuIds(skuIds: List<SkuId>): List<Spu> =
        if (skuIds.isEmpty()) emptyList()
        else jpaRepository.findBySkuIdsAndStatus(skuIds.map { it.value }.distinct(), CommodityStatus.PUBLISHED).map(Converter::toDomain)

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: Spu): Spu {
        val po =
            jpaRepository.findById(entity.id.value).orElse(null)?.also {
                Converter.copyToPO(entity, it)
            } ?: Converter.toPO(entity)
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

    internal object Converter {

        fun toPO(spu: Spu): SpuPO {
            return SpuPO(
                id = spu.id.value,
                merchantId = spu.merchantId.value,
                name = spu.name,
                description = spu.description,
                productTypeId = spu.productTypeId?.value,
                productAttributes = JsonUtils.toJsonString(spu.productAttributes),
                brandId = spu.brandId?.value,
                categoryIds = JsonUtils.toJsonString(spu.categoryIds.map { it.value }),
                localizedNames = spu.localizedNames?.let { JsonUtils.toJsonString(it.values) },
                localizedDescriptions =
                    spu.localizedDescriptions?.let { JsonUtils.toJsonString(it.values) },
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
                merchantCode = sku.merchantCode,
                barcode = sku.barcode,
                sourceSkuId = sku.sourceSkuId?.value,
            )
        }

        fun copyToPO(spu: Spu, po: SpuPO) {
            po.merchantId = spu.merchantId.value
            po.name = spu.name
            po.description = spu.description
            po.productTypeId = spu.productTypeId?.value
            po.productAttributes = JsonUtils.toJsonString(spu.productAttributes)
            po.brandId = spu.brandId?.value
            po.categoryIds = JsonUtils.toJsonString(spu.categoryIds.map { it.value })
            po.localizedNames = spu.localizedNames?.let { JsonUtils.toJsonString(it.values) }
            po.localizedDescriptions =
                spu.localizedDescriptions?.let { JsonUtils.toJsonString(it.values) }
            po.status = spu.status
            po.version = spu.version
            po.sourceSpuId = spu.sourceSpuId?.value

            val domainIds = spu.skus.map { it.id.value }.toSet()
            po.skus.removeIf { it.id !in domainIds }
            val existingById = po.skus.associateBy { it.id }
            spu.skus.forEach { sku ->
                val skuPO = existingById[sku.id.value]
                if (skuPO == null) {
                    po.skus += toSkuPO(sku, spu.id.value)
                } else {
                    skuPO.skuName = sku.skuName
                    skuPO.attributes = JsonUtils.toJsonString(sku.attributes)
                    skuPO.merchantCode = sku.merchantCode
                    skuPO.barcode = sku.barcode
                    skuPO.sourceSkuId = sku.sourceSkuId?.value
                }
            }
        }

        fun toDomain(po: SpuPO): Spu {
            return SpuImpl(
                id = SpuId(po.id),
                merchantId = MerchantId(po.merchantId),
                name = po.name,
                description = po.description,
                productTypeId = po.productTypeId?.let(::ProductTypeId),
                productAttributes = JsonUtils.deserialize(po.productAttributes),
                brandId = po.brandId?.let(::BrandId),
                categoryIds =
                    JsonUtils.deserialize<List<Long>>(po.categoryIds).map(::CategoryId).toSet(),
                localizedNames =
                    po.localizedNames?.let { LocalizedText(JsonUtils.deserialize(it)) },
                localizedDescriptions =
                    po.localizedDescriptions?.let { LocalizedText(JsonUtils.deserialize(it)) },
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
                merchantCode = po.merchantCode,
                barcode = po.barcode,
                sourceSkuId = po.sourceSkuId?.let(::SkuId),
            )
        }
    }
}
