package com.jstore.order.domain.order

import com.jstore.common.errors.Errors

object OrderErrors {
    val CORRESPONDING_GOODS_NOT_FOUND: Errors = Errors("corresponding goods resource not found", "Order.Resource.NotFound", 404)
    val ILLEGAL_STATE: Errors = Errors("invalid order state", "Order.State.Invalid", 400)

}