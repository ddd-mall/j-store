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
import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.commodity.*
import com.jstore.goods.domain.content.LocalizedText
import kotlin.test.Test
import kotlin.test.assertEquals

class SpuSnapshotContentTest {
    @Test
    fun `snapshot freezes published description and style content`() {
        val spu =
            SpuImpl(
                id = SpuId(1),
                name = "咖啡豆",
                description = "日晒处理",
                _status = CommodityStatus.PUBLISHED,
                _skus = mutableListOf(SkuImpl(SkuId(11), "250g", emptyList())),
                _version = 4,
            )
        val style =
            GoodsStyleImpl(
                id = GoodsStyleId(2),
                spuId = spu.id,
                _mainImages = mutableListOf("main-1"),
                _detailHtml = "<p>风味描述</p>",
                _skuImages = mutableMapOf(SkuId(11) to listOf("sku-11")),
            )

        val brandName = LocalizedText.of("zh-CN" to "发布时品牌")
        val snapshot =
            SpuSnapshotFactoryImpl(SnowFlakSequence()).createSnapshot(spu, style, brandName)

        assertEquals("日晒处理", snapshot.description)
        assertEquals(listOf("main-1"), snapshot.mainImages)
        assertEquals("<p>风味描述</p>", snapshot.detailHtml)
        assertEquals(listOf("sku-11"), snapshot.skuSnapshots.single().imageKeys)
        assertEquals("发布时品牌", snapshot.brandName?.get("zh-CN"))
    }

    @Test
    fun `snapshot freezes brand identity and localized name together`() {
        val spu =
            SpuImpl(
                id = SpuId(1),
                name = "咖啡豆",
                brandId = BrandId(5),
                _status = CommodityStatus.PUBLISHED,
                _skus = mutableListOf(SkuImpl(SkuId(11), "250g", emptyList())),
            )
        val brandName = LocalizedText.of("zh-CN" to "山野", "en-US" to "Mountain")

        val snapshot =
            SpuSnapshotFactoryImpl(SnowFlakSequence()).createSnapshot(spu, brandName = brandName)

        assertEquals(BrandId(5), snapshot.brandId)
        assertEquals(brandName, snapshot.brandName)
    }
}
