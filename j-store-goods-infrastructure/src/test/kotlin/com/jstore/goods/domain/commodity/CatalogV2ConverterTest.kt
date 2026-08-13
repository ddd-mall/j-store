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

import com.jstore.goods.domain.commodity.snapshot.SkuSnapshot
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshot
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotId
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogV2ConverterTest {
    @Test
    fun `spu converter preserves draft sku source identity`() {
        val draft =
            SpuImpl(
                id = SpuId(2),
                merchantId = MerchantId(7),
                name = "T恤草稿",
                _status = CommodityStatus.DRAFT,
                _skus =
                    mutableListOf(
                        SkuImpl(
                            SkuId(901),
                            "红色",
                            listOf(Attribute("color", "red")),
                            sourceSkuId = SkuId(101),
                        )
                    ),
                sourceSpuId = SpuId(1),
            )

        val roundTrip =
            SpuRepositoryImpl.Converter.toDomain(SpuRepositoryImpl.Converter.toPO(draft))

        assertEquals(SkuId(101), roundTrip.skus.single().sourceSkuId)
    }

    @Test
    fun `snapshot converter preserves versioned style content`() {
        val snapshot =
            SpuSnapshot(
                id = SpuSnapshotId(5),
                merchantId = MerchantId(7),
                spuId = SpuId(1),
                snapshotVersion = 4,
                spuName = "咖啡",
                description = "日晒",
                skuSnapshots =
                    listOf(
                        SkuSnapshot(
                            skuId = SkuId(11),
                            skuName = "250g",
                            attributes = emptyList(),
                            imageKeys = listOf("sku-image"),
                        )
                    ),
                mainImages = listOf("main-image"),
                detailHtml = "<p>detail</p>",
                createdAt = LocalDateTime.of(2026, 8, 13, 12, 0),
            )

        val roundTrip =
            SpuSnapshotRepositoryImpl.Converter.toDomain(
                SpuSnapshotRepositoryImpl.Converter.toPO(snapshot)
            )

        assertEquals(snapshot.mainImages, roundTrip.mainImages)
        assertEquals(snapshot.detailHtml, roundTrip.detailHtml)
        assertEquals(
            snapshot.skuSnapshots.single().imageKeys,
            roundTrip.skuSnapshots.single().imageKeys,
        )
    }
}
