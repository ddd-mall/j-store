package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.event.OrderCancelledEvent
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderItemSnapshot
import com.jstore.order.domain.order.event.OrderPaidEvent
import com.jstore.order.domain.order.event.OrderRefundApprovedEvent
import com.jstore.order.domain.order.event.OrderRefundRejectedEvent
import com.jstore.order.domain.order.event.OrderRefundRequestedEvent
import com.jstore.order.domain.order.event.OrderShippedEvent
import java.time.LocalDateTime
import java.util.LinkedList
import java.util.Queue

internal data class OrderStateSnapshot(
    val tradeStatus: TradeStatus,
    val paymentStatus: PaymentStatus,
    val fulfillmentStatus: FulfillmentStatus,
    val afterSaleStatus: AfterSaleStatus,
    val itemStatuses: List<OrderItemStatus>,
)

internal object OrderStateInvariants {
    fun violations(state: OrderStateSnapshot): List<String> = buildList {
        if (state.itemStatuses.isEmpty()) add("订单至少包含一个行项")
        if (state.tradeStatus == TradeStatus.CREATED &&
            (state.paymentStatus != PaymentStatus.UNPAID ||
                state.fulfillmentStatus != FulfillmentStatus.UNFULFILLED ||
                state.afterSaleStatus != AfterSaleStatus.NONE)
        ) add("CREATED 必须搭配 UNPAID / UNFULFILLED / NONE")
        if (state.paymentStatus == PaymentStatus.UNPAID && state.fulfillmentStatus != FulfillmentStatus.UNFULFILLED) {
            add("UNPAID 只允许 UNFULFILLED")
        }
        if (state.tradeStatus == TradeStatus.CLOSED && state.paymentStatus == PaymentStatus.UNPAID &&
            state.afterSaleStatus != AfterSaleStatus.NONE
        ) add("未支付关闭订单必须保持售后状态 NONE")
        if (state.fulfillmentStatus != FulfillmentStatus.UNFULFILLED && state.paymentStatus == PaymentStatus.UNPAID) {
            add("已进入履约的订单必须已支付")
        }
        if (state.tradeStatus == TradeStatus.COMPLETED &&
            (state.paymentStatus != PaymentStatus.PAID ||
                state.fulfillmentStatus != FulfillmentStatus.DELIVERED ||
                state.afterSaleStatus != AfterSaleStatus.NONE)
        ) add("COMPLETED 必须搭配 PAID / DELIVERED / NONE")

        val anyCanceled = state.itemStatuses.any { it == OrderItemStatus.CANCELED }
        val anyNotCanceled = state.itemStatuses.any { it != OrderItemStatus.CANCELED }
        val anyRefunding = state.itemStatuses.any { it == OrderItemStatus.REFUNDING }
        val allCanceled = state.itemStatuses.isNotEmpty() && state.itemStatuses.all { it == OrderItemStatus.CANCELED }

        if (state.paymentStatus == PaymentStatus.PARTIALLY_REFUNDED &&
            (state.afterSaleStatus != AfterSaleStatus.PARTIALLY_COMPLETED || !anyCanceled || !anyNotCanceled)
        ) add("PARTIALLY_REFUNDED 必须有已取消及未取消行项并搭配 PARTIALLY_COMPLETED")
        if (state.paymentStatus == PaymentStatus.REFUNDED &&
            (state.tradeStatus != TradeStatus.CLOSED || state.afterSaleStatus != AfterSaleStatus.COMPLETED || !allCanceled)
        ) add("REFUNDED 必须搭配 CLOSED / COMPLETED 且全部行项已取消")
        if (state.afterSaleStatus == AfterSaleStatus.PROCESSING &&
            (!anyRefunding || state.paymentStatus != PaymentStatus.PAID)
        ) add("PROCESSING 必须有退款中行项且支付状态为 PAID")
        if (state.afterSaleStatus == AfterSaleStatus.PARTIALLY_COMPLETED &&
            (state.paymentStatus != PaymentStatus.PARTIALLY_REFUNDED || !anyCanceled || !anyNotCanceled)
        ) add("PARTIALLY_COMPLETED 必须搭配 PARTIALLY_REFUNDED 并同时有已取消及未取消行项")
        if (state.afterSaleStatus == AfterSaleStatus.COMPLETED &&
            (state.paymentStatus != PaymentStatus.REFUNDED || !allCanceled)
        ) add("售后 COMPLETED 必须搭配 REFUNDED 且全部行项已取消")
        if (state.afterSaleStatus == AfterSaleStatus.NONE && anyRefunding) add("售后 NONE 不允许退款中行项")
        if (state.afterSaleStatus == AfterSaleStatus.NONE &&
            state.paymentStatus in setOf(PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED)
        ) add("售后 NONE 不允许退款支付状态")
    }

    fun requireValid(state: OrderStateSnapshot) {
        val violations = violations(state)
        require(violations.isEmpty()) { violations.joinToString("; ") }
    }
}

class OrderImpl(
    override val id: OrderId,
    override val buyerInfo: UserInfo,
    private val _items: MutableList<OrderItem>,
    override val recipientInfo: RecipientInfo,
    private var _tradeStatus: TradeStatus,
    private var _paymentStatus: PaymentStatus,
    private var _fulfillmentStatus: FulfillmentStatus,
    private var _afterSaleStatus: AfterSaleStatus,
    override val totalAmount: Price,
    private var _actualPay: Price,
    override val createTime: LocalDateTime = LocalDateTime.now(),
    private var _updateTime: LocalDateTime = LocalDateTime.now(),
) : Order {
    override val domainEventQueue: Queue<DomainEvent> = LinkedList()
    override val items: List<OrderItem> get() = _items.toList()
    override val tradeStatus: TradeStatus get() = _tradeStatus
    override val paymentStatus: PaymentStatus get() = _paymentStatus
    override val fulfillmentStatus: FulfillmentStatus get() = _fulfillmentStatus
    override val afterSaleStatus: AfterSaleStatus get() = _afterSaleStatus
    override val actualPay: Price get() = _actualPay
    override val updateTime: LocalDateTime get() = _updateTime

    init {
        OrderStateInvariants.requireValid(snapshot())
    }

    override fun confirmStock(): Result<Unit, BusinessError> {
        if (!matches(TradeStatus.CREATED, PaymentStatus.UNPAID, FulfillmentStatus.UNFULFILLED, AfterSaleStatus.NONE)) {
            return illegalState("确认库存")
        }
        val candidate = snapshot(tradeStatus = TradeStatus.ACTIVE)
        candidate.validate()?.let { return it }
        _tradeStatus = candidate.tradeStatus
        touch()
        return Success(Unit)
    }

    override fun markStockInsufficient(reason: String): Result<Unit, BusinessError> {
        if (!matches(TradeStatus.CREATED, PaymentStatus.UNPAID, FulfillmentStatus.UNFULFILLED, AfterSaleStatus.NONE)) {
            return illegalState("库存不足取消")
        }
        val candidate = snapshot(tradeStatus = TradeStatus.CLOSED)
        candidate.validate()?.let { return it }
        _tradeStatus = candidate.tradeStatus
        touch()
        publishEvent(OrderCancelledEvent(orderId = id, reason = reason))
        return Success(Unit)
    }

    override fun pay(paidAmount: Price): Result<Unit, BusinessError> {
        if (!matches(TradeStatus.ACTIVE, PaymentStatus.UNPAID, FulfillmentStatus.UNFULFILLED, AfterSaleStatus.NONE)) {
            return illegalState("支付")
        }
        val candidate = snapshot(paymentStatus = PaymentStatus.PAID)
        candidate.validate()?.let { return it }
        _paymentStatus = candidate.paymentStatus
        _actualPay = paidAmount
        touch()
        publishEvent(OrderPaidEvent(id, paidAmount, _items.map { OrderItemSnapshot(it.skuId, it.quantity) }))
        return Success(Unit)
    }

    override fun confirmForShipment(): Result<Unit, BusinessError> {
        if (!matches(TradeStatus.ACTIVE, PaymentStatus.PAID, FulfillmentStatus.UNFULFILLED, AfterSaleStatus.NONE)) {
            return illegalState("确认备货")
        }
        val candidate = snapshot(fulfillmentStatus = FulfillmentStatus.PENDING_SHIPMENT)
        candidate.validate()?.let { return it }
        _fulfillmentStatus = candidate.fulfillmentStatus
        touch()
        return Success(Unit)
    }

    override fun ship(): Result<Unit, BusinessError> {
        if (!matches(TradeStatus.ACTIVE, PaymentStatus.PAID, FulfillmentStatus.PENDING_SHIPMENT, AfterSaleStatus.NONE)) {
            return illegalState("发货")
        }
        val statuses = _items.map { OrderItemStatus.SHIPPING }
        val candidate = snapshot(fulfillmentStatus = FulfillmentStatus.SHIPPED, itemStatuses = statuses)
        candidate.validate()?.let { return it }
        _fulfillmentStatus = candidate.fulfillmentStatus
        mutableItems().forEach { it.status = OrderItemStatus.SHIPPING }
        touch()
        publishEvent(OrderShippedEvent(id))
        return Success(Unit)
    }

    override fun confirmDelivery(): Result<Unit, BusinessError> {
        if (!matches(TradeStatus.ACTIVE, PaymentStatus.PAID, FulfillmentStatus.SHIPPED, AfterSaleStatus.NONE)) {
            return illegalState("确认收货")
        }
        val statuses = _items.map { OrderItemStatus.SHIPPING_FINISHED }
        val candidate = snapshot(fulfillmentStatus = FulfillmentStatus.DELIVERED, itemStatuses = statuses)
        candidate.validate()?.let { return it }
        _fulfillmentStatus = candidate.fulfillmentStatus
        mutableItems().forEach { it.status = OrderItemStatus.SHIPPING_FINISHED }
        touch()
        return Success(Unit)
    }

    override fun complete(): Result<Unit, BusinessError> {
        if (!matches(TradeStatus.ACTIVE, PaymentStatus.PAID, FulfillmentStatus.DELIVERED, AfterSaleStatus.NONE)) {
            return illegalState("完成订单")
        }
        val candidate = snapshot(tradeStatus = TradeStatus.COMPLETED)
        candidate.validate()?.let { return it }
        _tradeStatus = candidate.tradeStatus
        touch()
        publishEvent(OrderCompletedEvent(id))
        return Success(Unit)
    }

    override fun cancel(reason: CancellationReason): Result<Unit, BusinessError> {
        val valid = _tradeStatus in setOf(TradeStatus.CREATED, TradeStatus.ACTIVE) &&
            _paymentStatus == PaymentStatus.UNPAID && _fulfillmentStatus == FulfillmentStatus.UNFULFILLED &&
            _afterSaleStatus == AfterSaleStatus.NONE
        if (!valid) return illegalState("取消订单")
        val statuses = _items.map { OrderItemStatus.CANCELED }
        val candidate = snapshot(tradeStatus = TradeStatus.CLOSED, itemStatuses = statuses)
        candidate.validate()?.let { return it }
        _tradeStatus = candidate.tradeStatus
        mutableItems().forEach { it.markCanceled() }
        touch()
        publishEvent(OrderCancelledEvent(id, reason.description))
        return Success(Unit)
    }

    override fun requestRefund(reason: RefundReason, itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        val valid = _tradeStatus == TradeStatus.ACTIVE &&
            _paymentStatus in setOf(PaymentStatus.PAID, PaymentStatus.PARTIALLY_REFUNDED) &&
            _fulfillmentStatus in setOf(FulfillmentStatus.UNFULFILLED, FulfillmentStatus.PENDING_SHIPMENT, FulfillmentStatus.DELIVERED) &&
            _afterSaleStatus in setOf(AfterSaleStatus.NONE, AfterSaleStatus.PROCESSING, AfterSaleStatus.PARTIALLY_COMPLETED)
        if (!valid) return illegalState("申请退款")
        val targets = resolveRefundItems(itemIds) { it.status !in setOf(OrderItemStatus.REFUNDING, OrderItemStatus.CANCELED) }
        if (targets is Failure) return targets
        val targetItems = (targets as Success).value
        val targetIds = targetItems.map { it.id }.toSet()
        val statuses = _items.map { if (it.id in targetIds) OrderItemStatus.REFUNDING else it.status }
        val afterSale = deriveAfterSaleStatus(_paymentStatus, statuses)
        val candidate = snapshot(afterSaleStatus = afterSale, itemStatuses = statuses)
        candidate.validate()?.let { return it }
        val refundAmount = Price.sumOf(targetItems.map { it.subtotal() })
        val requireReturn = _fulfillmentStatus in setOf(FulfillmentStatus.SHIPPED, FulfillmentStatus.DELIVERED)
        targetItems.forEach { it.enterRefunding() }
        _afterSaleStatus = candidate.afterSaleStatus
        touch()
        publishEvent(OrderRefundRequestedEvent(id, refundAmount, reason, requireReturn, itemIds))
        return Success(Unit)
    }

    override fun approveRefund(itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        if (_tradeStatus != TradeStatus.ACTIVE ||
            _afterSaleStatus !in setOf(AfterSaleStatus.PROCESSING, AfterSaleStatus.PARTIALLY_COMPLETED)
        ) return illegalState("批准退款")
        val targets = resolveRefundItems(itemIds) { it.status == OrderItemStatus.REFUNDING }
        if (targets is Failure) return targets
        val targetItems = (targets as Success).value
        val targetIds = targetItems.map { it.id }.toSet()
        val statuses = _items.map { if (it.id in targetIds) OrderItemStatus.CANCELED else it.status }
        val allCanceled = statuses.all { it == OrderItemStatus.CANCELED }
        val payment = if (allCanceled) PaymentStatus.REFUNDED else PaymentStatus.PARTIALLY_REFUNDED
        val trade = if (allCanceled) TradeStatus.CLOSED else TradeStatus.ACTIVE
        val afterSale = deriveAfterSaleStatus(payment, statuses)
        val candidate = snapshot(tradeStatus = trade, paymentStatus = payment, afterSaleStatus = afterSale, itemStatuses = statuses)
        candidate.validate()?.let { return it }
        val refundAmount = Price.sumOf(targetItems.map { it.subtotal() })
        val requireReturn = _fulfillmentStatus in setOf(FulfillmentStatus.SHIPPED, FulfillmentStatus.DELIVERED)
        targetItems.forEach { it.markCanceled() }
        _tradeStatus = candidate.tradeStatus
        _paymentStatus = candidate.paymentStatus
        _afterSaleStatus = candidate.afterSaleStatus
        touch()
        publishEvent(OrderRefundApprovedEvent(id, refundAmount, itemIds, requireReturn))
        return Success(Unit)
    }

    override fun rejectRefund(rejectReason: String, itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        if (_tradeStatus != TradeStatus.ACTIVE ||
            _afterSaleStatus !in setOf(AfterSaleStatus.PROCESSING, AfterSaleStatus.PARTIALLY_COMPLETED)
        ) return illegalState("拒绝退款")
        val targets = resolveRefundItems(itemIds) { it.status == OrderItemStatus.REFUNDING }
        if (targets is Failure) return targets
        val targetItems = (targets as Success).value
        val restored = targetItems.associate { item ->
            item.id to (item.previousItemStatus ?: return Failure(
                OrderErrors.REFUND_ITEM_INVALID_STATE.msg("行项 ${item.id} 缺少退款前状态")
            ))
        }
        val statuses = _items.map { restored[it.id] ?: it.status }
        val afterSale = deriveAfterSaleStatus(_paymentStatus, statuses)
        val candidate = snapshot(afterSaleStatus = afterSale, itemStatuses = statuses)
        candidate.validate()?.let { return it }
        targetItems.forEach { it.restoreFromRefunding() }
        _afterSaleStatus = candidate.afterSaleStatus
        touch()
        publishEvent(OrderRefundRejectedEvent(id, rejectReason, itemIds))
        return Success(Unit)
    }

    private fun matches(
        trade: TradeStatus,
        payment: PaymentStatus,
        fulfillment: FulfillmentStatus,
        afterSale: AfterSaleStatus,
    ) = _tradeStatus == trade && _paymentStatus == payment &&
        _fulfillmentStatus == fulfillment && _afterSaleStatus == afterSale

    private fun snapshot(
        tradeStatus: TradeStatus = _tradeStatus,
        paymentStatus: PaymentStatus = _paymentStatus,
        fulfillmentStatus: FulfillmentStatus = _fulfillmentStatus,
        afterSaleStatus: AfterSaleStatus = _afterSaleStatus,
        itemStatuses: List<OrderItemStatus> = _items.map { it.status },
    ) = OrderStateSnapshot(tradeStatus, paymentStatus, fulfillmentStatus, afterSaleStatus, itemStatuses)

    private fun OrderStateSnapshot.validate(): Failure<BusinessError>? {
        val violations = OrderStateInvariants.violations(this)
        return if (violations.isEmpty()) null else Failure(OrderErrors.ILLEGAL_STATE.msg(violations.joinToString("; ")))
    }

    private fun illegalState(operation: String): Failure<BusinessError> = Failure(
        OrderErrors.ILLEGAL_STATE.msg(
            "$operation 不允许：${_tradeStatus.name}/${_paymentStatus.name}/${_fulfillmentStatus.name}/${_afterSaleStatus.name}"
        )
    )

    private fun resolveRefundItems(
        itemIds: List<OrderItemId>,
        validState: (OrderItemImpl) -> Boolean,
    ): Result<List<OrderItemImpl>, BusinessError> {
        if (itemIds.isEmpty()) return Failure(OrderErrors.REFUND_ITEMS_EMPTY)
        if (itemIds.toSet().size != itemIds.size) {
            return Failure(OrderErrors.REFUND_ITEM_INVALID_STATE.msg("退款行项 ID 不得重复"))
        }
        val itemMap = mutableItems().associateBy { it.id }
        val targets = itemIds.map { id ->
            itemMap[id] ?: return Failure(OrderErrors.REFUND_ITEM_NOT_FOUND.msg("行项 $id 不属于本订单"))
        }
        targets.firstOrNull { !validState(it) }?.let {
            return Failure(OrderErrors.REFUND_ITEM_INVALID_STATE.msg("行项 ${it.id} 状态为 ${it.status.name}"))
        }
        return Success(targets)
    }

    private fun mutableItems(): List<OrderItemImpl> = _items.map {
        it as? OrderItemImpl ?: error("OrderImpl only supports OrderItemImpl children")
    }

    private fun touch() {
        _updateTime = LocalDateTime.now()
    }
}

private fun deriveAfterSaleStatus(
    paymentStatus: PaymentStatus,
    itemStatuses: List<OrderItemStatus>,
): AfterSaleStatus = when {
    paymentStatus == PaymentStatus.REFUNDED -> AfterSaleStatus.COMPLETED
    paymentStatus == PaymentStatus.PARTIALLY_REFUNDED -> AfterSaleStatus.PARTIALLY_COMPLETED
    itemStatuses.any { it == OrderItemStatus.REFUNDING } -> AfterSaleStatus.PROCESSING
    else -> AfterSaleStatus.NONE
}
