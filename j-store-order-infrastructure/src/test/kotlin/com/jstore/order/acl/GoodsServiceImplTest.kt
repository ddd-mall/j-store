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
package com.jstore.order.acl

import com.jstore.goods.api.GoodsSkuSnapshotInfo
import com.jstore.goods.api.GoodsSnapshotInfo
import com.jstore.goods.api.GoodsSnapshotQueryService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class GoodsServiceImplTest :
    FunSpec({
        test("queryGoods maps goods API snapshots without exposing goods domain objects") {
            var capturedSpuIds: List<Long>? = null
            val snapshotQueryService =
                object : GoodsSnapshotQueryService {
                    override fun queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo> {
                        capturedSpuIds = spuIds
                        return listOf(
                            GoodsSnapshotInfo(
                                spuId = 1001L,
                                merchantId = 7L,
                                snapshotVersion = 7L,
                                spuName = "Phone",
                                skuSnapshots =
                                    listOf(
                                        GoodsSkuSnapshotInfo(
                                            skuId = 2001L,
                                            skuName = "Black 128G",
                                            attributes =
                                                listOf("color" to "black", "storage" to "128G"),
                                        )
                                    ),
                            )
                        )
                    }
                }

            val service = GoodsServiceImpl(snapshotQueryService)

            val result =
                service.queryGoods(
                    listOf(
                        GoodsId(spuId = 1001L, skuId = 2001L),
                        GoodsId(spuId = 1001L, skuId = 9999L),
                        GoodsId(spuId = 1001L, skuId = 2001L),
                    )
                )

            capturedSpuIds shouldBe listOf(1001L)
            result shouldContainExactly
                listOf(
                    GoodsInfo(
                        id = GoodsId(spuId = 1001L, skuId = 2001L),
                        merchantId = 7L,
                        snapshotVersion = 7L,
                        spuName = "Phone",
                        skuName = "Black 128G",
                        attributes = listOf("color" to "black", "storage" to "128G"),
                    ),
                    GoodsInfo(
                        id = GoodsId(spuId = 1001L, skuId = 2001L),
                        merchantId = 7L,
                        snapshotVersion = 7L,
                        spuName = "Phone",
                        skuName = "Black 128G",
                        attributes = listOf("color" to "black", "storage" to "128G"),
                    ),
                )
        }
    })
