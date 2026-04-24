package com.jstore.order.domain.order

import com.jstore.common.properties.Price

/**
 * 订单行项实体实现
 */
class OrderItemImpl(
    override val id: OrderItemId,
    override val skuId: Long,
    override val spuId: Long,
    override val goodsName: String,
    override val skuDescription: String,
    override val quantity: Int,
    override val unitPrice: Price,
    override var status: OrderItemStatus = OrderItemStatus.NONE,
) : OrderItem {

    init {
        require(quantity > 0) { "商品数量必须大于0" }
    }

    override fun subtotal(): Price = unitPrice * quantity
}
