package com.jstore.order.domain.order.item

import com.jstore.common.properties.Price
import java.math.BigDecimal

interface OrderItem {

    val id: OrderItemId
    val quantity: BigDecimal
    val unitPrice: Price
    val totalPrice: Price

}