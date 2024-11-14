package com.jstore.com.jstore.order.saleorder
import com.jstore.com.jstore.order.saleorder.properties.OrderId

class SaleOrder(val id: OrderId<Long>) {

}


object SaleOrderFactory {
    fun createSaleOrder(id: OrderId<Long>): SaleOrder {
        return SaleOrder(id)
    }
}
