package com.jstore.order.domain.order.item

import com.jstore.common.properties.Id

data class OrderItemId(override val value: Long) : Id<Long>(value)