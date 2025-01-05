package com.jstore.order.stock

import com.jstore.order.acl.GoodsId
import java.math.BigDecimal

class StockDeductCMD(
    val goodsId: GoodsId,
    val quantity: BigDecimal
) {

}
