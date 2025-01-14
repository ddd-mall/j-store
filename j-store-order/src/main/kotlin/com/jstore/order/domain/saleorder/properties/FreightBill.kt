package com.jstore.order.domain.saleorder.properties

import com.jstore.common.properties.Price

data class FreightBill(
    val id: String,
    val fee: Price
)