package com.jstore.order.service

import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.SaleOrderFactory
import com.jstore.order.saleorder.SaleOrderRepository

class OrderSerive(private val saleOrderRepository: SaleOrderRepository) {
    fun createSaleOrder():SaleOrder {
        var saleOrder = SaleOrderFactory.assemblySaleOrder()
        saleOrder = saleOrderRepository.save(saleOrder);
        return saleOrder;
    }
}