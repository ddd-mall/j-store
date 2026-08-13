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

import com.jstore.common.errors.BusinessError
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd
import com.jstore.goods.domain.commodity.comand.SkuUpdateCmd

interface SpuFactory {
    fun create(createCmd: CommodityCreateCmd): Spu

    fun update(createCmd: CommodityCreateCmd, old: Spu): Spu

    fun createSku(cmd: SkuCreateCmd): Sku

    fun createSku(cmd: SkuUpdateCmd): Sku

    fun createDraftCopy(source: Spu): Result<Spu, BusinessError>
}

class SpuFactoryImpl(private val snowFlakSequence: SnowFlakSequence) : SpuFactory {

    override fun create(createCmd: CommodityCreateCmd): Spu {
        return SpuImpl(
            id = SpuId(snowFlakSequence.nextId()),
            merchantId = MerchantId(createCmd.merchantId),
            name = createCmd.spuName,
            description = createCmd.description,
            productTypeId = createCmd.productTypeId,
            productAttributes = createCmd.productAttributes,
            brandId = createCmd.brandId,
            categoryIds = createCmd.categoryIds,
            localizedNames = createCmd.localizedNames,
            localizedDescriptions = createCmd.localizedDescriptions,
            _status = CommodityStatus.DRAFT,
            _skus = ArrayList(),
        )
    }

    override fun update(createCmd: CommodityCreateCmd, old: Spu): Spu {
        require(createCmd.merchantId == old.merchantId.value) { "商品不能转移到其他商户" }
        return SpuImpl(
            id = old.id,
            merchantId = old.merchantId,
            name = createCmd.spuName,
            description = createCmd.description,
            productTypeId = createCmd.productTypeId,
            productAttributes = createCmd.productAttributes,
            brandId = createCmd.brandId,
            categoryIds = createCmd.categoryIds,
            localizedNames = createCmd.localizedNames,
            localizedDescriptions = createCmd.localizedDescriptions,
            _status = old.status,
            _skus = old.skus.toMutableList(),
            _version = old.version,
            sourceSpuId = old.sourceSpuId,
        )
    }

    override fun createSku(cmd: SkuCreateCmd): Sku {
        return SkuImpl(
            id = SkuId(snowFlakSequence.nextId()),
            skuName = cmd.skuName,
            attributes = cmd.attributes,
            merchantCode = cmd.merchantCode,
            barcode = cmd.barcode,
        )
    }

    override fun createSku(cmd: SkuUpdateCmd): Sku =
        SkuImpl(
            id = cmd.skuId,
            skuName = cmd.skuName,
            attributes = cmd.attributes,
            merchantCode = cmd.merchantCode,
            barcode = cmd.barcode,
        )

    /** 从已发布商品创建草稿副本。 */
    override fun createDraftCopy(source: Spu): Result<Spu, BusinessError> {
        if (source.status != CommodityStatus.PUBLISHED) {
            return Failure(CommodityErrors.ONLY_PUBLISHED_NEEDS_DRAFT)
        }
        val draft =
            SpuImpl(
                id = SpuId(snowFlakSequence.nextId()),
                merchantId = source.merchantId,
                name = source.name,
                description = source.description,
                productTypeId = source.productTypeId,
                productAttributes = source.productAttributes.toList(),
                brandId = source.brandId,
                categoryIds = source.categoryIds.toSet(),
                localizedNames = source.localizedNames,
                localizedDescriptions = source.localizedDescriptions,
                _status = CommodityStatus.DRAFT,
                _skus =
                    source.skus
                        .map { sku ->
                            SkuImpl(
                                id = SkuId(snowFlakSequence.nextId()),
                                skuName = sku.skuName,
                                attributes = sku.attributes.toList(),
                                merchantCode = sku.merchantCode,
                                barcode = sku.barcode,
                                sourceSkuId = sku.id,
                            )
                        }
                        .toMutableList(),
                _version = source.version,
                sourceSpuId = source.id,
            )
        return Success(draft)
    }
}
