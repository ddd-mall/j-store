package com.jstore.com.jstore.order.acl.freight

import com.jstore.order.saleorder.SaleOrder

interface FreightService {
    fun delivery(saleOrder: SaleOrder)

}