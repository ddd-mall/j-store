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
import com.jstore.goods.domain.commodity.event.CommodityArchivedEvent
import com.jstore.goods.domain.commodity.event.CommodityPublishedEvent

class SpuImpl(
    override val id: SpuId,
    override val merchantId: MerchantId = MerchantId(1),
    name: String,
    description: String = "",
    private var _status: CommodityStatus,
    private val _skus: MutableList<Sku>,
    private var _version: Long = 1L,
    override val sourceSpuId: SpuId? = null,
) : EventRecordingAggregateRoot<SpuId>(), Spu {

    private var _name: String = name
    private var _description: String = description

    override val name: String
        get() = _name

    override val description: String
        get() = _description

    override val skus: List<Sku>
        get() = _skus.toList()

    override val status: CommodityStatus
        get() = _status

    override val version: Long
        get() = _version

    override fun addSku(sku: Sku): Result<Unit, BusinessError> {
        // 检查属性组合是否重复
        val newKey = sku.attributes.map { "${it.key}:${it.value}" }.sorted()
        val duplicate = _skus.any { existing ->
            existing.attributes.map { "${it.key}:${it.value}" }.sorted() == newKey
        }
        if (duplicate) {
            return Failure(CommodityErrors.DUPLICATE_SKU_ATTRIBUTES)
        }
        _skus.add(sku)
        return Success(Unit)
    }

    override fun publish(): Result<Unit, BusinessError> {
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
        _skus.clear()
        _skus.addAll(draft.skus)
        _version++
        return Success(Unit)
    }
}
