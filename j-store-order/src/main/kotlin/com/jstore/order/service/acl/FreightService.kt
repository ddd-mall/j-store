package com.jstore.order.service.acl

import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.FreightBill

interface FreightService {
    fun delivery(order: Order)
    fun queryByIds( ids: Collection<String>) : List<FreightBill>
}