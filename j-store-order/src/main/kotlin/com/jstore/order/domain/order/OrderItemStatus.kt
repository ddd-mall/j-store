package com.jstore.order.domain.order

enum class OrderItemStatus {
    NONE,
    WAIT_SHIPPING,
    SHIPPING,
    SHIPPING_ERROR,
    SHIPPING_FINISHED,
    CANCELED,
}
