package com.jstore.order.acl

import com.jstore.goods.api.GoodsSnapshotQueryService

class GoodsServiceImpl(private val goodsSnapshotQueryService: GoodsSnapshotQueryService) :
    GoodsService {

    override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> {
        val spuIds = goodsId.map { it.spuId }.distinct()
        val snapshotMap =
            goodsSnapshotQueryService.queryLatestSnapshots(spuIds).associateBy { it.spuId }

        return goodsId.mapNotNull { gid ->
            val snapshot = snapshotMap[gid.spuId] ?: return@mapNotNull null
            val skuSnapshot =
                snapshot.skuSnapshots.find { it.skuId == gid.skuId } ?: return@mapNotNull null
            GoodsInfo(
                id = gid,
                merchantId = snapshot.merchantId,
                snapshotVersion = snapshot.snapshotVersion,
                spuName = snapshot.spuName,
                skuName = skuSnapshot.skuName,
                attributes = skuSnapshot.attributes,
            )
        }
    }
}
