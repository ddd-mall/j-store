package com.jstore.order.domain.order

import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.acl.GoodsId
import java.math.BigDecimal

class OrderItem(
    val id: OrderItemId,
    val goodsId: GoodsId,
    val goodsVersion: Long,
    val quantity: BigDecimal,
    val unitPrice: Price,
    val totalPrice: Price,
    var itemStatus: OrderItemStatus,
) {
    fun cancel() {
        if (itemStatus == OrderItemStatus.CANCELED) {
            return
        }
        itemStatus = OrderItemStatus.CANCELED
    }

    fun shipping() {
        if (itemStatus == OrderItemStatus.SHIPPING) {
            return
        }
        if (itemStatus == OrderItemStatus.CANCELED) {
            return
        }
        itemStatus = OrderItemStatus.SHIPPING
    }
}
data class OrderItemId(override val value: Long) : Id<Long>(value)


enum class OrderItemStatus {
    WAIT_SHIPPING, SHIPPING, SHIPPING_ERROR, SHIPPING_FINISHED, REFUNDING, CANCELED
}