package com.jstore.com.jstore.order.saleorder.properties

import com.jstore.com.jstore.framework.Identify

data class OrderId<T>(val value: T) : Identify {
}

