package com.jstore.order.acl.goods

import com.jstore.common.properties.Price
import com.jstore.order.service.acl.GoodsId
import com.jstore.order.service.acl.GoodsInfo
import com.jstore.order.service.acl.GoodsService
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class MockGoodsService: GoodsService {
    companion object {
        val mockGoodsId: List<GoodsId> = listOf(
            GoodsId(1, 1),
            GoodsId(2, 2),
            GoodsId(3, 3),
            GoodsId(4, 4)
        )
        private val goodsInfoMap: MutableMap<GoodsId, GoodsInfo> = ConcurrentHashMap()
    }

    init {
        mockGoodsId.filter { !goodsInfoMap.contains(it) }.forEach { id ->
            goodsInfoMap[id] = GoodsInfo(
                id,
                Random.nextLong(1, 1000),
                Price.Companion.Commonly.of(Random.nextInt(1, 1000000))
            )
        }
    }
    override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> {
        return goodsId.mapNotNull { goodsInfoMap[it] }
    }
}