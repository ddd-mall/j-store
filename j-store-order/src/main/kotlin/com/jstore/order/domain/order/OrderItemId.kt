package com.jstore.order.domain.order

import com.jstore.common.properties.Id

data class OrderItemId(override val value: Long) : Id<Long>(value)
