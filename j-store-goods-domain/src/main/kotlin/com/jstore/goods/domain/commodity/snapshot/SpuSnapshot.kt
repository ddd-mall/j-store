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

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.category.CategoryId
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.content.LocalizedText
import com.jstore.goods.domain.producttype.ProductTypeId
import java.time.LocalDateTime

/** SPU 快照 ID */
class SpuSnapshotId(override val value: Long) : Id<Long>(value)

/** SPU 快照 — 不可变值对象，记录某一时刻的商品资料。成交价格由 SalesOffer/订单快照追溯。 */
data class SpuSnapshot(
    override val id: SpuSnapshotId,
    /** 商品所属商户 */
    val merchantId: MerchantId,
    /** 原始 SPU ID */
    val spuId: SpuId,
    /** 快照版本号（与 SPU.version 对应） */
    val snapshotVersion: Long,
    /** 商品名称 */
    val spuName: String,
    /** 商品描述 */
    val description: String,
    /** SKU 快照列表 */
    val skuSnapshots: List<SkuSnapshot>,
    /** 发布时的主图对象 key，按展示顺序保存。 */
    val mainImages: List<String> = emptyList(),
    /** 发布时的详情内容。 */
    val detailHtml: String = "",
    val productTypeId: ProductTypeId? = null,
    val productAttributes: List<Attribute<String, String>> = emptyList(),
    val brandId: BrandId? = null,
    val categoryIds: Set<CategoryId> = emptySet(),
    val localizedNames: LocalizedText? = null,
    val localizedDescriptions: LocalizedText? = null,
    /** 快照创建时间 */
    val createdAt: LocalDateTime,
) : Entity<SpuSnapshotId>

/** SKU 快照 — 不可变值对象 */
data class SkuSnapshot(
    val skuId: SkuId,
    val skuName: String,
    val attributes: List<Attribute<String, String>>,
    val merchantCode: String? = null,
    val barcode: String? = null,
    val imageKeys: List<String> = emptyList(),
)
