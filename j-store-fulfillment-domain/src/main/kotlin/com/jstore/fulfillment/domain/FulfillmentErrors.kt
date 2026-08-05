package com.jstore.fulfillment.domain

import com.jstore.common.errors.BusinessError

object FulfillmentErrors {
    val NOT_FOUND = BusinessError("履约单不存在", "Fulfillment.NotFound", 404)
    val ORDER_CONFLICT = BusinessError("订单履约快照冲突", "Fulfillment.Order.Conflict", 409)
    val INVALID_STATE = BusinessError("履约单状态不允许当前操作", "Fulfillment.State.Invalid", 409)
    val SHIPPING_REFERENCE_INVALID =
        BusinessError("承运商或运单号无效", "Fulfillment.ShippingReference.Invalid", 400)
    val SHIPPING_REFERENCE_CONFLICT =
        BusinessError("履约单已关联其他运单", "Fulfillment.ShippingReference.Conflict", 409)
}
