package com.jstore.order.domain.order.item

import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderItemStatus
import java.math.BigDecimal

class NormalItem(
    override val id: OrderItemId,
    override val quantity: BigDecimal,
    override val unitPrice: Price,
    override val totalPrice: Price,
    override var itemStatus: OrderItemStatus,
) : OrderItem {
    override fun cancel() {
        if (itemStatus == OrderItemStatus.CANCELED) {
            return
        }
        itemStatus = OrderItemStatus.CANCELED
    }

    override fun shipping() {
        if (itemStatus == OrderItemStatus.SHIPPING) {
            return
        }
        if (itemStatus == OrderItemStatus.CANCELED) {
            return
        }
        itemStatus = OrderItemStatus.SHIPPING
    }
}