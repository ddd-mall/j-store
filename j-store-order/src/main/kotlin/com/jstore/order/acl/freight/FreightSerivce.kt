package com.jstore.com.jstore.order.acl.freight

import com.jstore.order.saleorder.SaleOrder

interface FreightSerivce {
    fun delivery(saleOrder: SaleOrder)

}