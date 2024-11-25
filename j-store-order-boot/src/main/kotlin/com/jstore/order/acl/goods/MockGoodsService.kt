package com.jstore.com.jstore.order.acl.goods

import com.jstore.order.acl.goods.GoodsId
import com.jstore.order.acl.goods.GoodsInfo
import com.jstore.order.acl.goods.GoodsService
import org.springframework.stereotype.Service

@Service
class MockGoodsService: GoodsService {
    override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> {
        TODO("Not yet implemented")
    }
}