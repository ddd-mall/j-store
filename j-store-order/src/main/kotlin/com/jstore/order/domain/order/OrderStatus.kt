package com.jstore.order.domain.order

enum class OrderStatus {
    CREATED,

    PAY_REQUESTED,
    PAYED,

    SHIPPING_REQUESTED,
    SELLER_SHIPPING,
    COMPLETE,

    REFUNDING,
    WAIT_FOR_BUYER_SHIPPING,
    BUYER_SHIPPING,
    CANCELED,
    CLOSE,
}