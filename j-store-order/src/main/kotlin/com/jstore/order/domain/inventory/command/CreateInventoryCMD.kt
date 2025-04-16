package com.jstore.order.domain.inventory.command

import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.order.OrderId
import java.math.BigDecimal

class CreateInventoryCMD(
    val orderId: OrderId,
    val goodsId: GoodsId,
    val quantity: BigDecimal,
)