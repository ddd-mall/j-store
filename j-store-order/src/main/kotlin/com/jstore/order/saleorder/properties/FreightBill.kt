package com.jstore.order.saleorder.properties

import com.jstore.common.properties.Price

data class FreightBill(
    val id: String,
    val fee: Price
)