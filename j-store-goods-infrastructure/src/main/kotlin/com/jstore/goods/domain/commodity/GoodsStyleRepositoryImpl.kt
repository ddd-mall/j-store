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
import com.jstore.goods.domain.commodity.persistence.GoodsStylePO
import com.jstore.goods.domain.commodity.persistence.GoodsStylePOJpaRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class GoodsStyleRepositoryImpl(private val jpaRepository: GoodsStylePOJpaRepository) :
    GoodsStyleRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: GoodsStyle): GoodsStyle {
        val po = Converter.toPO(entity)
        po.updateTime = LocalDateTime.now()
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: GoodsStyleId): GoodsStyle? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findBySpuId(spuId: SpuId): GoodsStyle? {
        return jpaRepository.findBySpuId(spuId.value)?.let { Converter.toDomain(it) }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun delete(style: GoodsStyle) {
        jpaRepository.deleteById(style.id.value)
    }

    internal object Converter {

        fun toPO(goodsStyle: GoodsStyle): GoodsStylePO {
            val skuImagesMap: Map<String, List<String>> =
                goodsStyle.skuImages
                    .map { (skuId, images) ->
                        skuId.value.toString() to images
                    }
                    .toMap()

            return GoodsStylePO(
                id = goodsStyle.id.value,
                spuId = goodsStyle.spuId.value,
                mainImages = JsonUtils.toJsonString(goodsStyle.mainImages),
                detailHtml = goodsStyle.detailHtml,
                skuImages = JsonUtils.toJsonString(skuImagesMap),
            )
        }

        fun toDomain(po: GoodsStylePO): GoodsStyle {
            val mainImages: List<String> = JsonUtils.deserialize(po.mainImages)
            val skuImagesRaw: Map<String, List<String>> = JsonUtils.deserialize(po.skuImages)
            val skuImages: MutableMap<SkuId, List<String>> =
                skuImagesRaw
                    .map { (key, images) ->
                        SkuId(key.toLong()) to images
                    }
                    .toMap()
                    .toMutableMap()

            return GoodsStyleImpl(
                id = GoodsStyleId(po.id),
                spuId = SpuId(po.spuId),
                _mainImages = mainImages.toMutableList(),
                _detailHtml = po.detailHtml,
                _skuImages = skuImages,
            )
        }
    }
}
