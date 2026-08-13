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
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.framework.RecordsDomainEvents
import com.jstore.common.properties.Id
import com.jstore.common.utils.Result
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.category.CategoryId
import com.jstore.goods.domain.content.LocalizedText
import com.jstore.goods.domain.producttype.ProductTypeId

data class MerchantId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "merchantId must be positive" }
    }
}

/** TODO: 商品的 Copy-on-Write 流程应该适用于所有状态 */
interface Spu : AggregateRoot<SpuId>, RecordsDomainEvents {
    /** 商品所属商户。一个商品的全部 SKU 共享同一商户归属。 */
    val merchantId: MerchantId

    /** 商品名称 */
    val name: String

    /** 商品描述 */
    val description: String

    /** 可复用的商品资料 schema。null 表示尚未绑定类型的历史/最小商品。 */
    val productTypeId: ProductTypeId?

    /** SPU 级结构化属性值。 */
    val productAttributes: List<Attribute<String, String>>

    val brandId: BrandId?

    val categoryIds: Set<CategoryId>

    val localizedNames: LocalizedText?

    val localizedDescriptions: LocalizedText?

    /** SKU 列表（只读视图） */
    val skus: List<Sku>

    /** 商品状态 */
    val status: CommodityStatus

    /** 版本号（每次快照递增） */
    val version: Long

    /** 源商品 ID：null 表示原始商品，非 null 表示该 SPU 是指定源商品的草稿副本 */
    val sourceSpuId: SpuId?

    /** 添加 SKU */
    fun addSku(sku: Sku): Result<Unit, BusinessError>

    /** 修改草稿中的 SKU。替换对象必须使用被修改 SKU 的草稿身份。 */
    fun updateSku(sku: Sku): Result<Unit, BusinessError>

    /** 删除草稿中的 SKU。 */
    fun removeSku(skuId: SkuId): Result<Unit, BusinessError>

    /** 发布商品资料：DRAFT → PUBLISHED。 */
    fun publish(): Result<Unit, BusinessError>

    /** 归档商品资料：PUBLISHED → ARCHIVED。 */
    fun archive(): Result<Unit, BusinessError>

    /** 将草稿副本的内容合并到当前 SPU（领域方法） */
    fun mergeFromDraft(draft: Spu): Result<Unit, BusinessError>
}
