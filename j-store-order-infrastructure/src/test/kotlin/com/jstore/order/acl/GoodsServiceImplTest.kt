package com.jstore.order.acl

import com.jstore.common.properties.Price
import com.jstore.goods.api.GoodsSnapshotInfo
import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.goods.api.GoodsSkuSnapshotInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class GoodsServiceImplTest : FunSpec({

    test("queryGoods maps goods API snapshots without exposing goods domain objects") {
        var capturedSpuIds: List<Long>? = null
        val snapshotQueryService = object : GoodsSnapshotQueryService {
            override fun queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo> {
                capturedSpuIds = spuIds
                return listOf(
                    GoodsSnapshotInfo(
                        spuId = 1001L,
                        snapshotVersion = 7L,
                        spuName = "Phone",
                        skuSnapshots = listOf(
                            GoodsSkuSnapshotInfo(
                                skuId = 2001L,
                                skuName = "Black 128G",
                                attributes = listOf("color" to "black", "storage" to "128G"),
                                price = Price.ofFen(399900),
                            )
                        ),
                    )
                )
            }
        }

        val service = GoodsServiceImpl(snapshotQueryService)

        val result = service.queryGoods(
            listOf(
                GoodsId(spuId = 1001L, skuId = 2001L),
                GoodsId(spuId = 1001L, skuId = 9999L),
                GoodsId(spuId = 1001L, skuId = 2001L),
            )
        )

        capturedSpuIds shouldBe listOf(1001L)
        result shouldContainExactly listOf(
            GoodsInfo(
                id = GoodsId(spuId = 1001L, skuId = 2001L),
                snapshotVersion = 7L,
                spuName = "Phone",
                skuName = "Black 128G",
                attributes = listOf("color" to "black", "storage" to "128G"),
                price = Price.ofFen(399900),
            ),
            GoodsInfo(
                id = GoodsId(spuId = 1001L, skuId = 2001L),
                snapshotVersion = 7L,
                spuName = "Phone",
                skuName = "Black 128G",
                attributes = listOf("color" to "black", "storage" to "128G"),
                price = Price.ofFen(399900),
            ),
        )
    }
})
