package com.jstore.order.acl

import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.properties.FreightBill

interface FreightService {
    fun delivery(saleOrder: SaleOrder)
    fun queryByIds( ids: Collection<String>) : List<FreightBill>
}