package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import java.time.LocalDateTime
import java.time.Instant
import com.jstore.order.domain.aftersale.AfterSaleId

/** 订单聚合根接口。交易、支付、履约和售后状态分别表达并行的业务事实。 */
interface Order : AgreeGate<OrderId> {
    override val id: OrderId

    /** 买家信息（不可变值对象） */
    val buyerInfo: UserInfo

    /** 订单行项（只读视图） */
    val items: List<OrderItem>

    /** 收货信息（不可变值对象） */
    val recipientInfo: RecipientInfo

    val tradeStatus: TradeStatus
    val paymentStatus: PaymentStatus
    val fulfillmentStatus: FulfillmentStatus
    val totalRefundedAmount: Price
    val approvedRefundFacts: List<RefundFact>

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

    /** 买家主动取消订单（未支付阶段） */
    fun cancel(reason: CancellationReason): Result<Unit, BusinessError>

    fun refundEligibility(): Result<RefundEligibility, BusinessError>
    fun registerApprovedAfterSale(afterSaleId: AfterSaleId, items: List<ApprovedRefundItem>, occurredAt: Instant): Result<RefundProjectionResult, BusinessError>
}
