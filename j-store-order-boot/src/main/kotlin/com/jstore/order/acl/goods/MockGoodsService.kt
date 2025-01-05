package com.jstore.com.jstore.order.acl.goods

import com.jstore.common.properties.Price
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

@Service
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
        return goodsInfoMap.values.toList()
    }
}