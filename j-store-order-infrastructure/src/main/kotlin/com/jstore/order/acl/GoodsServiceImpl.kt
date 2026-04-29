package com.jstore.order.acl

import com.jstore.goods.domain.commodity.SpuId
import com.jstore.goods.domain.commodity.snapshot.SpuSnapshotRepository
import org.springframework.stereotype.Service

@Service
class GoodsServiceImpl(
    private val spuSnapshotRepository: SpuSnapshotRepository,
) : GoodsService {

    override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> {
        val spuIds = goodsId.map { it.spuId }.distinct()
        val snapshotMap = spuIds.mapNotNull { spuId ->
            spuSnapshotRepository.findLatestBySpuId(SpuId(spuId))?.let { spuId to it }
        }.toMap()

        return goodsId.mapNotNull { gid ->
            val snapshot = snapshotMap[gid.spuId] ?: return@mapNotNull null
            val skuSnapshot = snapshot.skuSnapshots.find { it.skuId.value == gid.skuId }
                ?: return@mapNotNull null
            GoodsInfo(
                id = gid,
                snapshotVersion = snapshot.snapshotVersion,
                spuName = snapshot.spuName,
                skuName = skuSnapshot.skuName,
                attributes = skuSnapshot.attributes.map { it.key to it.value },
                price = skuSnapshot.price,
            )
        }
    }
}
