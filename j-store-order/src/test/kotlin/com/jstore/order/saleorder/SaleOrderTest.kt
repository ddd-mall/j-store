package com.jstore.order.saleorder

import com.jstore.com.jstore.order.saleorder.SaleOrderFactory
import com.jstore.com.jstore.order.saleorder.properties.OrderId


class SaleOrderTest {
    fun saleOrderCreateTest() {
        val saleOrder = SaleOrderFactory.createSaleOrder(OrderId(1L))
        assert(saleOrder.id.value == 1L)
    }
}