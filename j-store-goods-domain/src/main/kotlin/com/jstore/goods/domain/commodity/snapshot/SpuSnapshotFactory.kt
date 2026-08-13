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
package com.jstore.goods.domain.commodity.snapshot

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.domain.commodity.GoodsStyle
import com.jstore.goods.domain.commodity.Spu
import java.time.LocalDateTime

interface SpuSnapshotFactory {
    fun createSnapshot(spu: Spu, style: GoodsStyle? = null): SpuSnapshot
}

class SpuSnapshotFactoryImpl(private val snowFlakSequence: SnowFlakSequence) : SpuSnapshotFactory {

    override fun createSnapshot(spu: Spu, style: GoodsStyle?): SpuSnapshot {
        return SpuSnapshot(
            id = SpuSnapshotId(snowFlakSequence.nextId()),
            merchantId = spu.merchantId,
            spuId = spu.id,
            snapshotVersion = spu.version,
            spuName = spu.name,
            description = spu.description,
            skuSnapshots =
                spu.skus.map { sku ->
                    SkuSnapshot(
                        skuId = sku.id,
                        skuName = sku.skuName,
                        attributes = sku.attributes.toList(),
                        merchantCode = sku.merchantCode,
                        barcode = sku.barcode,
                        imageKeys = style?.skuImages?.get(sku.id).orEmpty(),
                    )
                },
            mainImages = style?.mainImages.orEmpty(),
            detailHtml = style?.detailHtml.orEmpty(),
            productTypeId = spu.productTypeId,
            productAttributes = spu.productAttributes.toList(),
            brandId = spu.brandId,
            categoryIds = spu.categoryIds.toSet(),
            localizedNames = spu.localizedNames,
            localizedDescriptions = spu.localizedDescriptions,
            createdAt = LocalDateTime.now(),
        )
    }
}
