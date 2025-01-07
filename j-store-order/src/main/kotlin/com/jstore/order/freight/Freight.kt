package com.jstore.order.freight

import com.jstore.common.properties.Price

interface Freight {
    fun calculate(): Price
    fun delivery()

}