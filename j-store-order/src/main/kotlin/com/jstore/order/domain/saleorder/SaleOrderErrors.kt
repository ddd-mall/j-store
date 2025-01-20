package com.jstore.order.domain.saleorder

import com.jstore.common.errors.Errors

object SaleOrderErrors {
    val CorrespondingGoodsNotFound: Errors = Errors("corresponding goods resource not found", "Order.Resource.NotFound", 400)
}