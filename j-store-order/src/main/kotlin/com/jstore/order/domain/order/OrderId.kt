package com.jstore.order.domain.order

import com.jstore.common.properties.Id

data class OrderId(override val value: Long) : Id<Long>(value)