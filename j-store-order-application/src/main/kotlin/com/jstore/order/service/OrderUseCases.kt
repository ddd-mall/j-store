package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.query.Page
import com.jstore.common.utils.Result
import com.jstore.order.domain.aftersale.AfterSale
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.command.AfterSaleApproveCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCancelCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.aftersale.command.AfterSaleReceiveReturnCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRejectCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRetryRefundCMD
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.SaleAuthorizationRef
import com.jstore.order.domain.order.SuccessfulRefundItem
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCreateCMD
import java.time.Instant

/**
 * Stable inbound port for order use cases. Framework transaction concerns belong to boot adapters.
 */
interface OrderUseCase {
    fun getOrderById(buyerId: Long, orderId: OrderId): Result<Order, BusinessError>

    fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order>

    fun createOrder(cmd: OrderCreateCMD): Result<Order, BusinessError>

    fun recordSaleAuthorized(
        orderId: OrderId,
        authorizations: List<SaleAuthorizationRef>,
    ): Result<Unit, BusinessError>

    fun markSaleAuthorizationFailed(orderId: OrderId, reason: String): Result<Unit, BusinessError>

    fun confirmStock(orderId: OrderId): Result<Unit, BusinessError>

    fun markStockInsufficient(orderId: OrderId, reason: String): Result<Unit, BusinessError>

    fun recordPaymentCaptured(
        orderId: OrderId,
        paymentReference: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun recordFulfillmentPrepared(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError>

    fun recordShipmentDispatched(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError>

    fun recordShipmentDelivered(
        orderId: OrderId,
        fulfillmentReference: String,
    ): Result<Boolean, BusinessError>

    fun recordRefundSucceeded(
        orderId: OrderId,
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun completeOrder(orderId: OrderId): Result<Unit, BusinessError>

    fun cancelOrder(buyerId: Long, cmd: OrderCancelCMD): Result<Unit, BusinessError>
}

interface AfterSaleUseCase {
    fun findById(id: AfterSaleId): Result<AfterSale, BusinessError>

    fun listByOrderForAccess(orderId: OrderId): Result<AfterSaleOrderAccess, BusinessError>

    fun create(cmd: AfterSaleCreateCMD): Result<AfterSale, BusinessError>

    fun approve(cmd: AfterSaleApproveCMD): Result<AfterSale, BusinessError>

    fun reject(cmd: AfterSaleRejectCMD): Result<AfterSale, BusinessError>

    fun cancel(cmd: AfterSaleCancelCMD): Result<AfterSale, BusinessError>

    fun receiveReturn(cmd: AfterSaleReceiveReturnCMD): Result<AfterSale, BusinessError>

    fun retryRefund(cmd: AfterSaleRetryRefundCMD): Result<AfterSale, BusinessError>

    fun recordRefundSucceeded(
        afterSaleId: AfterSaleId,
        refundId: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>

    fun recordRefundFailed(
        afterSaleId: AfterSaleId,
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError>
}
