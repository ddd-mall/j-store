package com.jstore.order.domain.aftersale

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId

interface AfterSaleItem : Entity<AfterSaleItemId> {
    val orderId: OrderId; val orderItemId: OrderItemId; val requestedQuantity: Int
    val requestedAmount: Price; val currency: String; val eligibilitySnapshot: RefundEligibilitySnapshot
}
data class AfterSaleItemImpl(override val id: AfterSaleItemId, override val orderId: OrderId, override val orderItemId: OrderItemId, override val requestedQuantity: Int, override val requestedAmount: Price, override val currency: String, override val eligibilitySnapshot: RefundEligibilitySnapshot) : AfterSaleItem {
    init { require(requestedQuantity > 0 && requestedQuantity <= eligibilitySnapshot.refundableQuantity); require(requestedAmount > Price.ZERO && requestedAmount <= eligibilitySnapshot.refundableAmount); require(currency == eligibilitySnapshot.currency) }
}
