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

    /** 结算和履约主体。订单内所有行项必须属于该商户。 */
    val merchantId: MerchantId

    /** 买家信息（不可变值对象） */
    val buyerInfo: UserInfo

    /** 订单行项（只读视图） */
    val items: List<OrderItem>

    /** 收货信息（不可变值对象） */
    val recipientInfo: RecipientInfo

    val tradeStatus: TradeStatus
    val paymentStatus: PaymentStatus
    val fulfillmentStatus: FulfillmentStatus
    val refundedAmount: Price
    val successfulRefundFacts: List<RefundFact>

    /** 下单时冻结的成交金额组成 */
    val amountSnapshot: OrderAmountSnapshot

    /** 已由支付上下文确认捕获的金额 */
    val paidAmount: Price

    /** 对应的支付聚合标识，支付成功前为空 */
    val paymentReference: String?

    /** 对应的履约聚合标识，履约建立前为空 */
    val fulfillmentReference: String?

    /** 创建时间 */
    val createTime: LocalDateTime

    /** 更新时间 */
    val updateTime: LocalDateTime

    /** 库存预扣成功，转为待支付 */
    fun confirmStock(): Result<Unit, BusinessError>

    /** 库存不足，取消订单 */
    fun markStockInsufficient(reason: String): Result<Unit, BusinessError>

    /** 登记支付上下文已经发生的全额捕获事实。 */
    fun recordPaymentCaptured(
        paymentReference: String,
        capturedAmount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    /** 登记履约单已进入待发货。 */
    fun recordFulfillmentPrepared(fulfillmentReference: String): Result<Boolean, BusinessError>

    /** 登记履约单已发货。 */
    fun recordShipmentDispatched(fulfillmentReference: String): Result<Boolean, BusinessError>

    /** 登记履约单已送达。 */
    fun recordShipmentDelivered(fulfillmentReference: String): Result<Boolean, BusinessError>

    /** 完成订单 */
    fun complete(): Result<Unit, BusinessError>

    /** 买家主动取消订单（未支付阶段） */
    fun cancel(reason: CancellationReason): Result<Unit, BusinessError>

    fun refundEligibility(): Result<RefundEligibility, BusinessError>
    fun recordRefundSucceeded(
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ): Result<RefundProjectionResult, BusinessError>
}
