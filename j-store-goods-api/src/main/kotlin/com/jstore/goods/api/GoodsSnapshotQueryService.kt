package com.jstore.goods.api

interface GoodsSnapshotQueryService {
    fun queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo>
}

data class GoodsSnapshotInfo(
    val spuId: Long,
    val merchantId: Long,
    val snapshotVersion: Long,
    val spuName: String,
    val skuSnapshots: List<GoodsSkuSnapshotInfo>,
)

data class GoodsSkuSnapshotInfo(
    val skuId: Long,
    val skuName: String,
    val attributes: List<Pair<String, String>>,
)
