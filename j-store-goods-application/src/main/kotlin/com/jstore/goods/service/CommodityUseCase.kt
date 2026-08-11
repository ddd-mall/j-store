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
package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.goods.domain.commodity.GoodsStyle
import com.jstore.goods.domain.commodity.Spu
import com.jstore.goods.domain.commodity.SpuId
import com.jstore.goods.domain.commodity.comand.CommodityCreateCmd
import com.jstore.goods.domain.commodity.comand.GoodsStyleSaveCmd
import com.jstore.goods.domain.commodity.comand.SkuCreateCmd
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshot

/** 商品上下文对外暴露的应用用例端口。 */
interface CommodityUseCase {
    fun createOrUpdate(cmd: CommodityCreateCmd): Result<Spu, BusinessError>

    fun addSku(cmd: SkuCreateCmd): Result<Spu, BusinessError>

    fun publish(spuId: SpuId): Result<SpuSnapshot, BusinessError>

    fun archive(spuId: SpuId): Result<Unit, BusinessError>

    fun getDraft(spuId: SpuId): Result<Spu, BusinessError>

    fun publishDraft(draftSpuId: SpuId): Result<SpuSnapshot, BusinessError>

    fun discardDraft(draftSpuId: SpuId): Result<Unit, BusinessError>

    fun saveGoodsStyle(cmd: GoodsStyleSaveCmd): Result<GoodsStyle, BusinessError>
}
