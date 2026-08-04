package com.jstore.order.domain.order

import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.AfterSaleId
import java.time.Instant

data class RefundableOrderItem(val orderItemId: OrderItemId, val purchasedQuantity: Int, val purchasedAmount: Price, val refundedQuantity: Int, val refundedAmount: Price, val refundableQuantity: Int, val refundableAmount: Price, val skuId: Long, val spuId: Long, val goodsName: String, val skuDescription: String)
data class RefundEligibility(val orderId: OrderId, val merchantId: MerchantId, val buyerId: Long, val paymentStatus: PaymentStatus, val tradeStatus: TradeStatus, val fulfillmentStatus: FulfillmentStatus, val paidAmount: Price, val totalRefundedAmount: Price, val currency: String, val items: List<RefundableOrderItem>)
data class SuccessfulRefundItem(val orderItemId: OrderItemId, val quantity: Int, val amount: Price)
data class RefundProjectionResult(val newlyRegistered: Boolean)
data class RefundFact(val refundId: String, val afterSaleId: AfterSaleId, val orderItemId: OrderItemId, val quantity: Int, val amount: Price, val occurredAt: Instant)
