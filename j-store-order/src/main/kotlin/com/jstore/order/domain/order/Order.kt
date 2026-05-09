package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import java.time.LocalDateTime

/**
 * 订单聚合根接口
 * 正向流程: 创建(PENDING_PAYMENT) → 支付(PAID) → 确认备货(PENDING_SHIPMENT) → 发货(SHIPPED) → 确认收货(DELIVERED) → 完成(COMPLETED)
 */
interface Order : AgreeGate<OrderId> {
    override val id: OrderId

    /** 买家信息（不可变值对象） */
    val buyerInfo: UserInfo

    /** 订单行项（只读视图） */
    val items: List<OrderItem>

    /** 收货信息（不可变值对象） */
    val recipientInfo: RecipientInfo

    /** 订单状态 */
    val status: OrderStatus

    /** 订单总金额 */
    val totalAmount: Price

    /** 实际支付金额 */
    val actualPay: Price

    /** 创建时间 */
    val createTime: LocalDateTime

    /** 更新时间 */
    val updateTime: LocalDateTime

    /** 支付 */
    fun pay(paidAmount: Price): Result<Unit, BusinessError>

    /** 库存预扣成功，转为待支付 */
    fun confirmStock(): Result<Unit, BusinessError>

    /** 库存不足，取消订单 */
    fun markStockInsufficient(reason: String): Result<Unit, BusinessError>

    /** 确认备货（支付确认后转为待发货） */
    fun confirmForShipment(): Result<Unit, BusinessError>

    /** 发货 */
    fun ship(): Result<Unit, BusinessError>

    /** 确认收货 */
    fun confirmDelivery(): Result<Unit, BusinessError>

    /** 完成订单 */
    fun complete(): Result<Unit, BusinessError>

    /** 进入 REFUNDING 前的 Order 级别状态，用于退款拒绝时恢复 */
    val previousStatus: OrderStatus?

    /** 买家主动取消订单（未支付阶段） */
    fun cancel(reason: CancellationReason): Result<Unit, BusinessError>

    /** 申请退款（已支付未发货 / 已签收退货退款），指定行项 */
    fun requestRefund(reason: RefundReason, itemIds: List<OrderItemId>): Result<Unit, BusinessError>

    /** 卖家批准退款，指定行项 */
    fun approveRefund(itemIds: List<OrderItemId>): Result<Unit, BusinessError>

    /** 卖家拒绝退款，指定行项 */
    fun rejectRefund(rejectReason: String, itemIds: List<OrderItemId>): Result<Unit, BusinessError>
}
