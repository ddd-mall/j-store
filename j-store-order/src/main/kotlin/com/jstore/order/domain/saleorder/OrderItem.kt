package com.jstore.order.domain.saleorder

import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.service.acl.GoodsId
import java.math.BigDecimal

data class OrderItem(
    val id: OrderItemId,
    val goodsId: GoodsId,
    val goodsVersion: Long,
    val quantity: BigDecimal,
    val unitPrice: Price,
    val totalPrice: Price,
)
data class OrderItemId(override val value: Long) : Id<Long>(value)
