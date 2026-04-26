package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.geo.I18nGeoAddress
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
import java.util.*

/**
 * 订单聚合根实现
 * 封装所有正向流程的状态转移逻辑和领域事件发布
 */
class OrderImpl(
    override val id: OrderId,
    override val buyerInfo: UserInfo,
    private val _items: MutableList<OrderItem>,
    override val shippingAddress: I18nGeoAddress,
    override val shippingDetailAddress: String? = null,
    private var _status: OrderStatus,
    override val totalAmount: Price,
    private var _actualPay: Price,
    override val createTime: LocalDateTime = LocalDateTime.now(),
    private var _updateTime: LocalDateTime = LocalDateTime.now(),
    private var _previousStatus: OrderStatus? = null,
) : Order {

    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    override val items: List<OrderItem> get() = _items.toList()
    override val status: OrderStatus get() = _status
    override val actualPay: Price get() = _actualPay
    override val updateTime: LocalDateTime get() = _updateTime
    override val previousStatus: OrderStatus? get() = _previousStatus

    override fun confirmStock(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.PENDING_PAYMENT)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法确认库存"))
        }
        _status = OrderStatus.PENDING_PAYMENT
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    override fun markStockInsufficient(reason: String): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.CANCELLED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法取消"))
        }
        _status = OrderStatus.CANCELLED
        _updateTime = LocalDateTime.now()
        publishEvent(OrderCancelledEvent(orderId = id, reason = reason))
        return Success(Unit)
    }

    override fun pay(paidAmount: Price): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.PAID)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法执行支付"))
        }
        _status = OrderStatus.PAID
        _actualPay = paidAmount
        _updateTime = LocalDateTime.now()
        publishEvent(OrderPaidEvent(
            orderId = id,
            paidAmount = paidAmount,
            items = _items.map { OrderItemSnapshot(skuId = it.skuId, quantity = it.quantity) }
        ))
        return Success(Unit)
    }

    override fun confirmForShipment(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.PENDING_SHIPMENT)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法确认备货"))
        }
        _status = OrderStatus.PENDING_SHIPMENT
        _updateTime = LocalDateTime.now()
        return Success(Unit)
    }

    // TODO: 这里需要通过acl对接仓储系统,对接发货流程
    override fun ship(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.SHIPPED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法执行发货"))
        }
        _status = OrderStatus.SHIPPED
        _updateTime = LocalDateTime.now()
        _items.filterIsInstance<OrderItemImpl>().forEach { it.status = OrderItemStatus.SHIPPING }
        publishEvent(OrderShippedEvent(orderId = id))
        return Success(Unit)
    }

    // TODO: 这里需要通过acl对接仓储系统,通过出库事件回调,流转发货状态
    override fun confirmDelivery(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.DELIVERED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法确认收货"))
        }
        _status = OrderStatus.DELIVERED
        _updateTime = LocalDateTime.now()
        _items.filterIsInstance<OrderItemImpl>().forEach { it.status = OrderItemStatus.SHIPPING_FINISHED }
        return Success(Unit)
    }

    override fun complete(): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.COMPLETED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法完成订单"))
        }
        _status = OrderStatus.COMPLETED
        _updateTime = LocalDateTime.now()
        publishEvent(OrderCompletedEvent(orderId = id))
        return Success(Unit)
    }

    override fun cancel(reason: CancellationReason): Result<Unit, BusinessError> {
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.CANCELLED)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法取消"))
        }
        _status = OrderStatus.CANCELLED
        _updateTime = LocalDateTime.now()
        _items.filterIsInstance<OrderItemImpl>().forEach { it.status = OrderItemStatus.CANCELED }
        publishEvent(OrderCancelledEvent(orderId = id, reason = reason.description))
        return Success(Unit)
    }

    override fun requestRefund(reason: RefundReason, itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        // 1. 校验 Order 状态可转移到 REFUNDING
        if (!OrderStatusTransitionRules.isValidTransition(_status, OrderStatus.REFUNDING)) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("当前状态${_status.name}无法申请退款"))
        }
        // 2. 校验 itemIds 非空
        if (itemIds.isEmpty()) {
            return Failure(OrderErrors.REFUND_ITEMS_EMPTY)
        }
        // 3. 校验 itemIds 属于本订单
        val itemMap = _items.filterIsInstance<OrderItemImpl>().associateBy { it.id }
        val targetItems = itemIds.map { itemId ->
            itemMap[itemId] ?: return Failure(OrderErrors.REFUND_ITEM_NOT_FOUND.msg("行项 $itemId 不属于本订单"))
        }
        // 4. 校验目标行项状态（不能是 REFUNDING 或 CANCELED）
        targetItems.forEach { item ->
            if (item.status == OrderItemStatus.REFUNDING || item.status == OrderItemStatus.CANCELED) {
                return Failure(OrderErrors.REFUND_ITEM_INVALID_STATE.msg("行项 ${item.id} 状态为 ${item.status.name}，无法申请退款"))
            }
        }
        // 5. 记录 Order 级别 previousStatus（仅首次进入 REFUNDING 时记录）
        if (_status != OrderStatus.REFUNDING) {
            _previousStatus = _status
            _status = OrderStatus.REFUNDING
        }
        _updateTime = LocalDateTime.now()
        // 6. 将选中行项进入 REFUNDING，记录 previousItemStatus
        targetItems.forEach { it.enterRefunding() }
        // 7. 计算退款金额
        val refundAmount = Price.sumOf(targetItems.map { it.subtotal() })
        // 8. 发布事件（商品已出库需走退货流程）
        val shipped = _previousStatus == OrderStatus.SHIPPED || _previousStatus == OrderStatus.DELIVERED
        publishEvent(OrderRefundRequestedEvent(
            orderId = id,
            refundAmount = refundAmount,
            reason = reason,
            requireReturn = shipped,
            refundItemIds = itemIds
        ))
        return Success(Unit)
    }

    override fun approveRefund(itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        if (_status != OrderStatus.REFUNDING) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("仅 REFUNDING 状态可批准退款"))
        }
        if (itemIds.isEmpty()) {
            return Failure(OrderErrors.REFUND_ITEMS_EMPTY)
        }
        val itemMap = _items.filterIsInstance<OrderItemImpl>().associateBy { it.id }
        val targetItems = itemIds.map { itemId ->
            itemMap[itemId] ?: return Failure(OrderErrors.REFUND_ITEM_NOT_FOUND.msg("行项 $itemId 不属于本订单"))
        }
        targetItems.forEach { item ->
            if (item.status != OrderItemStatus.REFUNDING) {
                return Failure(OrderErrors.REFUND_ITEM_INVALID_STATE.msg("行项 ${item.id} 状态为 ${item.status.name}，无法批准退款"))
            }
        }
        targetItems.forEach { it.markCanceled() }
        _updateTime = LocalDateTime.now()
        val refundAmount = Price.sumOf(targetItems.map { it.subtotal() })
        val allItemsTerminal = _items.filterIsInstance<OrderItemImpl>()
            .all { it.status == OrderItemStatus.CANCELED }
        if (allItemsTerminal) {
            _status = OrderStatus.CANCELLED
            _previousStatus = null
        }
        // 商品已出库（SHIPPED/DELIVERED）需走退货流程，未出库可直接释放库存
        val shipped = _previousStatus == OrderStatus.SHIPPED || _previousStatus == OrderStatus.DELIVERED
        publishEvent(OrderRefundApprovedEvent(
            orderId = id,
            refundAmount = refundAmount,
            approvedItemIds = itemIds,
            requireReturn = shipped
        ))
        return Success(Unit)
    }

    override fun rejectRefund(rejectReason: String, itemIds: List<OrderItemId>): Result<Unit, BusinessError> {
        if (_status != OrderStatus.REFUNDING) {
            return Failure(OrderErrors.ILLEGAL_STATE.msg("仅 REFUNDING 状态可拒绝退款"))
        }
        if (itemIds.isEmpty()) {
            return Failure(OrderErrors.REFUND_ITEMS_EMPTY)
        }
        val itemMap = _items.filterIsInstance<OrderItemImpl>().associateBy { it.id }
        val targetItems = itemIds.map { itemId ->
            itemMap[itemId] ?: return Failure(OrderErrors.REFUND_ITEM_NOT_FOUND.msg("行项 $itemId 不属于本订单"))
        }
        targetItems.forEach { item ->
            if (item.status != OrderItemStatus.REFUNDING) {
                return Failure(OrderErrors.REFUND_ITEM_INVALID_STATE.msg("行项 ${item.id} 状态为 ${item.status.name}，无法拒绝退款"))
            }
        }
        targetItems.forEach { it.restoreFromRefunding() }
        _updateTime = LocalDateTime.now()
        val anyItemRefunding = _items.filterIsInstance<OrderItemImpl>()
            .any { it.status == OrderItemStatus.REFUNDING }
        if (!anyItemRefunding) {
            val restoreStatus = _previousStatus
                ?: return Failure(OrderErrors.ILLEGAL_STATE.msg("无法恢复状态：previousStatus 为空"))
            _status = restoreStatus
            _previousStatus = null
        }
        publishEvent(OrderRefundRejectedEvent(
            orderId = id,
            rejectReason = rejectReason,
            rejectedItemIds = itemIds
        ))
        return Success(Unit)
    }
}
