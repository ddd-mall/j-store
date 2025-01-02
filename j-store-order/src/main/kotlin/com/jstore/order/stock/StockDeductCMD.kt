package com.jstore.order.stock

import com.jstore.order.acl.goods.GoodsId
import java.math.BigDecimal

class StockDeductCMD(
    val goodsId: GoodsId,
    val quantity: BigDecimal
) {

}