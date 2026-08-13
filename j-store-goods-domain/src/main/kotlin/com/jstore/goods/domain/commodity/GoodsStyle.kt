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
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

interface GoodsStyle : AggregateRoot<GoodsStyleId> {
    val spuId: SpuId
    val mainImages: List<String>
    val detailHtml: String
    val skuImages: Map<SkuId, List<String>>

    fun updateMainImages(images: List<String>): Result<Unit, BusinessError>

    fun updateDetailHtml(html: String): Result<Unit, BusinessError>

    fun updateSkuImages(skuId: SkuId, images: List<String>): Result<Unit, BusinessError>

    /** Replace the complete versioned content, removing stale SKU media entries. */
    fun replaceContent(
        mainImages: List<String>,
        detailHtml: String,
        skuImages: Map<SkuId, List<String>>,
    ): Result<Unit, BusinessError>
}

class GoodsStyleImpl(
    override val id: GoodsStyleId,
    override val spuId: SpuId,
    private var _mainImages: MutableList<String>,
    private var _detailHtml: String,
    private val _skuImages: MutableMap<SkuId, List<String>>,
) : GoodsStyle {
    override val mainImages: List<String>
        get() = _mainImages.toList()

    override val detailHtml: String
        get() = _detailHtml

    override val skuImages: Map<SkuId, List<String>>
        get() = _skuImages.toMap()

    override fun updateMainImages(images: List<String>): Result<Unit, BusinessError> {
        if (images.size != images.distinct().size) {
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        }
        _mainImages = images.toMutableList()
        return Success(Unit)
    }

    override fun updateDetailHtml(html: String): Result<Unit, BusinessError> {
        _detailHtml = html
        return Success(Unit)
    }

    override fun updateSkuImages(skuId: SkuId, images: List<String>): Result<Unit, BusinessError> {
        if (images.size != images.distinct().size) {
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        }
        _skuImages[skuId] = images.toList()
        return Success(Unit)
    }

    override fun replaceContent(
        mainImages: List<String>,
        detailHtml: String,
        skuImages: Map<SkuId, List<String>>,
    ): Result<Unit, BusinessError> {
        if (
            mainImages.size != mainImages.distinct().size ||
                skuImages.values.any { it.size != it.distinct().size }
        ) {
            return Failure(CommodityErrors.DUPLICATE_IMAGE_KEY)
        }
        _mainImages = mainImages.toMutableList()
        _detailHtml = detailHtml
        _skuImages.clear()
        _skuImages.putAll(skuImages.mapValues { it.value.toList() })
        return Success(Unit)
    }
}
