package com.jstore.order.domain.order

import com.jstore.common.properties.Id

data class OrderId(override val value: Long) : Id<Long>(value)

data class MerchantId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "merchantId must be positive" }
    }
}
