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
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.category.CategoryId
import com.jstore.goods.domain.commodity.event.CommodityArchivedEvent
import com.jstore.goods.domain.commodity.event.CommodityPublishedEvent
import com.jstore.goods.domain.content.LocalizedText
import com.jstore.goods.domain.producttype.ProductTypeId

class SpuImpl(
    override val id: SpuId,
    override val merchantId: MerchantId = MerchantId(1),
    name: String,
    description: String = "",
    productTypeId: ProductTypeId? = null,
    productAttributes: List<Attribute<String, String>> = emptyList(),
    brandId: BrandId? = null,
    categoryIds: Set<CategoryId> = emptySet(),
    localizedNames: LocalizedText? = null,
    localizedDescriptions: LocalizedText? = null,
    private var _status: CommodityStatus,
    private val _skus: MutableList<Sku>,
    private var _version: Long = 1L,
    override val sourceSpuId: SpuId? = null,
) : EventRecordingAggregateRoot<SpuId>(), Spu {

    private var _name: String = name
    private var _description: String = description
    private var _productTypeId: ProductTypeId? = productTypeId
    private var _productAttributes: List<Attribute<String, String>> = productAttributes.toList()
    private var _brandId: BrandId? = brandId
    private var _categoryIds: Set<CategoryId> = categoryIds.toSet()
    private var _localizedNames: LocalizedText? = localizedNames
    private var _localizedDescriptions: LocalizedText? = localizedDescriptions

    override val name: String
        get() = _name

    override val description: String
        get() = _description

    override val productTypeId: ProductTypeId?
        get() = _productTypeId

    override val productAttributes: List<Attribute<String, String>>
        get() = _productAttributes.toList()

    override val brandId: BrandId?
        get() = _brandId

    override val categoryIds: Set<CategoryId>
        get() = _categoryIds.toSet()

    override val localizedNames: LocalizedText?
        get() = _localizedNames

    override val localizedDescriptions: LocalizedText?
        get() = _localizedDescriptions

    override val skus: List<Sku>
        get() = _skus.toList()

    override val status: CommodityStatus
        get() = _status

    override val version: Long
        get() = _version

    override fun addSku(sku: Sku): Result<Unit, BusinessError> {
        if (_status != CommodityStatus.DRAFT)
            return Failure(CommodityErrors.SKU_DIRECT_EDIT_REJECTED)
        validateSkuUniqueness(sku).onFailure {
            return Failure(it)
        }
        _skus.add(sku)
        return Success(Unit)
    }

    override fun updateSku(sku: Sku): Result<Unit, BusinessError> {
        if (_status != CommodityStatus.DRAFT)
            return Failure(CommodityErrors.SKU_DIRECT_EDIT_REJECTED)
        val index = _skus.indexOfFirst { it.id == sku.id }
        if (index < 0) return Failure(CommodityErrors.SKU_NOT_FOUND)
        validateSkuUniqueness(sku, ignoredSkuId = sku.id).onFailure {
            return Failure(it)
        }
        val existing = _skus[index]
        _skus[index] =
            SkuImpl(
                id = sku.id,
                skuName = sku.skuName,
                attributes = sku.attributes.toList(),
                merchantCode = sku.merchantCode,
                barcode = sku.barcode,
                sourceSkuId = sku.sourceSkuId ?: existing.sourceSkuId,
            )
        return Success(Unit)
    }

    override fun removeSku(skuId: SkuId): Result<Unit, BusinessError> {
        if (_status != CommodityStatus.DRAFT)
            return Failure(CommodityErrors.SKU_DIRECT_EDIT_REJECTED)
        val removed = _skus.removeIf { it.id == skuId }
        return if (removed) Success(Unit) else Failure(CommodityErrors.SKU_NOT_FOUND)
    }

    override fun publish(): Result<Unit, BusinessError> {
        if (sourceSpuId != null) {
            return Failure(CommodityErrors.DRAFT_COPY_DIRECT_PUBLISH_REJECTED)
        }
        if (_status != CommodityStatus.DRAFT) {
            return Failure(
                CommodityErrors.INVALID_STATUS_TRANSITION.msg("只有草稿状态可以发布，当前状态: $_status")
            )
        }
        if (_skus.isEmpty()) {
            return Failure(CommodityErrors.NO_SKU_FOR_PUBLISH)
        }
        _version++
        _status = CommodityStatus.PUBLISHED
        raise(CommodityPublishedEvent(spuId = id, snapshotVersion = _version))
        return Success(Unit)
    }

    override fun archive(): Result<Unit, BusinessError> {
        if (_status != CommodityStatus.PUBLISHED) {
            return Failure(
                CommodityErrors.INVALID_STATUS_TRANSITION.msg("只有已发布商品可以归档，当前状态: $_status")
            )
        }
        _status = CommodityStatus.ARCHIVED
        raise(CommodityArchivedEvent(spuId = id))
        return Success(Unit)
    }

    /** 将草稿副本的内容合并到已发布 SPU。 */
    override fun mergeFromDraft(draft: Spu): Result<Unit, BusinessError> {
        if (draft.merchantId != merchantId) {
            return Failure(CommodityErrors.INVALID_STATUS_TRANSITION.msg("不能合并其他商户的商品草稿"))
        }
        if (_status != CommodityStatus.PUBLISHED) {
            return Failure(
                CommodityErrors.INVALID_STATUS_TRANSITION.msg("只有已发布商品可以合并草稿，当前状态: $_status")
            )
        }
        if (draft.skus.isEmpty()) {
            return Failure(CommodityErrors.DRAFT_NO_SKU_FOR_PUBLISH)
        }
        _name = draft.name
        _description = draft.description
        _productTypeId = draft.productTypeId
        _productAttributes = draft.productAttributes.toList()
        _brandId = draft.brandId
        _categoryIds = draft.categoryIds.toSet()
        _localizedNames = draft.localizedNames
        _localizedDescriptions = draft.localizedDescriptions
        _skus.clear()
        _skus.addAll(draft.skus.map { it.asPublishedSku() })
        _version++
        raise(CommodityPublishedEvent(spuId = id, snapshotVersion = _version))
        return Success(Unit)
    }

    private fun validateSkuUniqueness(
        candidate: Sku,
        ignoredSkuId: SkuId? = null,
    ): Result<Unit, BusinessError> {
        val others = _skus.filter { it.id != ignoredSkuId }
        val candidateKey = candidate.attributes.map { "${it.key}:${it.value}" }.sorted()
        if (
            others.any { existing ->
                existing.attributes.map { "${it.key}:${it.value}" }.sorted() == candidateKey
            }
        ) {
            return Failure(CommodityErrors.DUPLICATE_SKU_ATTRIBUTES)
        }
        if (
            !candidate.merchantCode.isNullOrBlank() &&
                others.any { it.merchantCode == candidate.merchantCode }
        ) {
            return Failure(CommodityErrors.DUPLICATE_MERCHANT_CODE)
        }
        if (!candidate.barcode.isNullOrBlank() && others.any { it.barcode == candidate.barcode }) {
            return Failure(CommodityErrors.DUPLICATE_BARCODE)
        }
        return Success(Unit)
    }

    private fun Sku.asPublishedSku(): Sku =
        SkuImpl(
            id = sourceSkuId ?: id,
            skuName = skuName,
            attributes = attributes.toList(),
            merchantCode = merchantCode,
            barcode = barcode,
        )
}
