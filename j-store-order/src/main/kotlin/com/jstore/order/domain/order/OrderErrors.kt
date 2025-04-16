package com.jstore.order.domain.order

import com.jstore.common.errors.Errors

object OrderErrors {
    val CorrespondingGoodsNotFound: Errors = Errors("corresponding goods resource not found", "Order.Resource.NotFound", 400)
}