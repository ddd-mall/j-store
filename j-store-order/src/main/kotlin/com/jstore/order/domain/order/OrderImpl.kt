package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.order.event.*
import java.time.Instant
import java.time.LocalDateTime
import java.util.LinkedList
import java.util.Queue

class OrderImpl(
    override val id: OrderId,
    override val buyerInfo: UserInfo,
    private val _items: MutableList<OrderItem>,
    override val recipientInfo: RecipientInfo,
    private var _tradeStatus: TradeStatus,
    private var _paymentStatus: PaymentStatus,
    private var _fulfillmentStatus: FulfillmentStatus,
    override val totalAmount: Price,
    private var _actualPay: Price,
    private var _totalRefundedAmount: Price = Price.ZERO,
    private val refundFacts: MutableList<RefundFact> = mutableListOf(),
    override val createTime: LocalDateTime = LocalDateTime.now(),
    private var _updateTime: LocalDateTime = LocalDateTime.now(),
) : Order {
    override val domainEventQueue: Queue<DomainEvent> = LinkedList()
    override val items get() = _items.toList(); override val tradeStatus get() = _tradeStatus
    override val paymentStatus get() = _paymentStatus; override val fulfillmentStatus get() = _fulfillmentStatus
    override val actualPay get() = _actualPay; override val totalRefundedAmount get() = _totalRefundedAmount
    override val approvedRefundFacts get() = refundFacts.toList()
    override val updateTime get() = _updateTime
    init { require(_items.isNotEmpty()); require(_totalRefundedAmount == Price.sumOf(_items.map { it.refundedAmount })); require(_totalRefundedAmount <= _actualPay) }

    override fun confirmStock() = transition(_tradeStatus == TradeStatus.CREATED && unpaid(), "确认库存") { _tradeStatus = TradeStatus.ACTIVE }
    override fun markStockInsufficient(reason: String) = transition(_tradeStatus == TradeStatus.CREATED && unpaid(), "库存不足取消") { _tradeStatus = TradeStatus.CLOSED; publishEvent(OrderCancelledEvent(id, reason)) }
    override fun pay(paidAmount: Price): Result<Unit, BusinessError> = transition(_tradeStatus == TradeStatus.ACTIVE && unpaid(), "支付") { _paymentStatus = PaymentStatus.PAID; _actualPay = paidAmount; publishEvent(OrderPaidEvent(id, paidAmount, _items.map { OrderItemSnapshot(it.skuId, it.quantity) })) }
    override fun confirmForShipment() = transition(_tradeStatus == TradeStatus.ACTIVE && _paymentStatus == PaymentStatus.PAID && _fulfillmentStatus == FulfillmentStatus.UNFULFILLED, "确认备货") { _fulfillmentStatus = FulfillmentStatus.PENDING_SHIPMENT }
    override fun ship() = transition(_tradeStatus == TradeStatus.ACTIVE && _paymentStatus == PaymentStatus.PAID && _fulfillmentStatus == FulfillmentStatus.PENDING_SHIPMENT, "发货") { _fulfillmentStatus = FulfillmentStatus.SHIPPED; mutableItems().forEach { it.status = OrderItemStatus.SHIPPING }; publishEvent(OrderShippedEvent(id)) }
    override fun confirmDelivery() = transition(_tradeStatus == TradeStatus.ACTIVE && _paymentStatus == PaymentStatus.PAID && _fulfillmentStatus == FulfillmentStatus.SHIPPED, "确认收货") { _fulfillmentStatus = FulfillmentStatus.DELIVERED; mutableItems().forEach { it.status = OrderItemStatus.SHIPPING_FINISHED } }
    override fun complete() = transition(_tradeStatus == TradeStatus.ACTIVE && _paymentStatus == PaymentStatus.PAID && _fulfillmentStatus == FulfillmentStatus.DELIVERED, "完成订单") { _tradeStatus = TradeStatus.COMPLETED; publishEvent(OrderCompletedEvent(id)) }
    override fun cancel(reason: CancellationReason) = transition((_tradeStatus == TradeStatus.CREATED || _tradeStatus == TradeStatus.ACTIVE) && unpaid(), "取消订单") { _tradeStatus = TradeStatus.CLOSED; mutableItems().forEach { it.markCanceled() }; publishEvent(OrderCancelledEvent(id, reason.description)) }

    override fun refundEligibility(): Result<RefundEligibility, BusinessError> {
        if (_paymentStatus !in setOf(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED) || _tradeStatus !in setOf(TradeStatus.ACTIVE, TradeStatus.COMPLETED) || _fulfillmentStatus !in setOf(FulfillmentStatus.UNFULFILLED, FulfillmentStatus.PENDING_SHIPMENT, FulfillmentStatus.SHIPPED, FulfillmentStatus.DELIVERED)) return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
        val refundable = _items.filter { it.refundableQuantity > 0 && it.refundableAmount > Price.ZERO }.map { RefundableOrderItem(it.id, it.quantity, it.purchasedAmount, it.refundedQuantity, it.refundedAmount, it.refundableQuantity, it.refundableAmount, it.skuId, it.spuId, it.goodsName, it.skuDescription) }
        if (refundable.isEmpty()) return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
        return Success(RefundEligibility(id, buyerInfo.uid, _paymentStatus, _tradeStatus, _fulfillmentStatus, _actualPay, _totalRefundedAmount, refundable))
    }

    override fun registerApprovedAfterSale(afterSaleId: AfterSaleId, items: List<ApprovedRefundItem>, occurredAt: Instant): Result<RefundProjectionResult, BusinessError> {
        if (refundFacts.any { it.afterSaleId == afterSaleId }) return Success(RefundProjectionResult(false))
        if (items.isEmpty() || items.map { it.orderItemId }.toSet().size != items.size || items.any { it.quantity <= 0 || it.amount <= Price.ZERO }) return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
        val byId = mutableItems().associateBy { it.id }
        for (item in items) { val target = byId[item.orderItemId] ?: return Failure(OrderErrors.REFUND_PROJECTION_INVALID); if (item.quantity > target.refundableQuantity || item.amount > target.refundableAmount) return Failure(OrderErrors.REFUND_PROJECTION_INVALID) }
        val amount = Price.sumOf(items.map { it.amount }); if (_totalRefundedAmount + amount > _actualPay) return Failure(OrderErrors.REFUND_PROJECTION_INVALID)
        items.forEach { item -> byId.getValue(item.orderItemId).registerRefund(item.quantity, item.amount); refundFacts += RefundFact(afterSaleId, item.orderItemId, item.quantity, item.amount, occurredAt) }
        _totalRefundedAmount += amount
        if (_totalRefundedAmount == _actualPay) { _paymentStatus = PaymentStatus.REFUNDED; _tradeStatus = TradeStatus.CLOSED } else _paymentStatus = PaymentStatus.PARTIALLY_REFUNDED
        touch(); return Success(RefundProjectionResult(true))
    }
    private fun unpaid() = _paymentStatus == PaymentStatus.UNPAID && _fulfillmentStatus == FulfillmentStatus.UNFULFILLED
    private inline fun transition(valid: Boolean, operation: String, action: () -> Unit): Result<Unit, BusinessError> { if (!valid) return Failure(OrderErrors.ILLEGAL_STATE.msg("$operation 不允许")); action(); touch(); return Success(Unit) }
    private fun mutableItems() = _items.map { it as OrderItemImpl }
    private fun touch() { _updateTime = LocalDateTime.now() }
}
