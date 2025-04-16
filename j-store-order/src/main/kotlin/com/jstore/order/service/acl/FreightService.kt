package com.jstore.order.service.acl

import com.jstore.order.domain.saleorder.SaleOrder
import com.jstore.order.domain.saleorder.properties.FreightBill

interface FreightService {
    fun delivery(saleOrder: SaleOrder)
    fun queryByIds( ids: Collection<String>) : List<FreightBill>
}