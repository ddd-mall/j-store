package com.jstore.order.domain.order

import com.jstore.common.properties.Price

data class FreightBill(
    val id: String,
    val fee: Price
)