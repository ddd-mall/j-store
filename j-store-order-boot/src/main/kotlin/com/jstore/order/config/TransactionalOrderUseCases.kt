package com.jstore.order.config

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.Page
import com.jstore.common.properties.Price
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
import com.jstore.order.domain.order.SuccessfulRefundItem
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCreateCMD
import com.jstore.order.service.AfterSaleOrderAccess
import com.jstore.order.service.AfterSaleUseCase
import com.jstore.order.service.OrderUseCase
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** Spring transaction boundary around the framework-free application service. */
class TransactionalOrderUseCase(
    private val delegate: OrderUseCase,
    transactionManager: PlatformTransactionManager,
) : OrderUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun getOrderById(orderId: OrderId) = read { delegate.getOrderById(orderId) }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> =
        read { delegate.pageListByUserId(uid, currentPage, pageSize) }

    override fun createOrder(cmd: OrderCreateCMD) = write { delegate.createOrder(cmd) }

    override fun confirmStock(orderId: OrderId) = write { delegate.confirmStock(orderId) }

    override fun markStockInsufficient(orderId: OrderId, reason: String) =
        write { delegate.markStockInsufficient(orderId, reason) }

    override fun recordPaymentCaptured(
        orderId: OrderId,
        paymentReference: String,
        amount: Price,
        currency: String,
        occurredAt: Instant,
    ) = write {
        delegate.recordPaymentCaptured(orderId, paymentReference, amount, currency, occurredAt)
    }

    override fun recordFulfillmentPrepared(orderId: OrderId, fulfillmentReference: String) =
        write { delegate.recordFulfillmentPrepared(orderId, fulfillmentReference) }

    override fun recordShipmentDispatched(orderId: OrderId, fulfillmentReference: String) =
        write { delegate.recordShipmentDispatched(orderId, fulfillmentReference) }

    override fun recordShipmentDelivered(orderId: OrderId, fulfillmentReference: String) =
        write { delegate.recordShipmentDelivered(orderId, fulfillmentReference) }

    override fun recordRefundSucceeded(
        orderId: OrderId,
        refundId: String,
        afterSaleId: AfterSaleId,
        items: List<SuccessfulRefundItem>,
        occurredAt: Instant,
    ) = write { delegate.recordRefundSucceeded(orderId, refundId, afterSaleId, items, occurredAt) }

    override fun completeOrder(orderId: OrderId) = write { delegate.completeOrder(orderId) }

    override fun cancelOrder(cmd: OrderCancelCMD) = write { delegate.cancelOrder(cmd) }

    private fun <T> read(block: () -> T): T = requireNotNull(read.execute { block() })

    private fun <T> write(block: () -> T): T = requireNotNull(write.execute { block() })
}

/** Spring transaction boundary for after-sale commands and consistent reads. */
class TransactionalAfterSaleUseCase(
    private val delegate: AfterSaleUseCase,
    transactionManager: PlatformTransactionManager,
) : AfterSaleUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun findById(id: AfterSaleId): Result<AfterSale, BusinessError> =
        read { delegate.findById(id) }

    override fun listByOrderForAccess(orderId: OrderId): Result<AfterSaleOrderAccess, BusinessError> =
        read { delegate.listByOrderForAccess(orderId) }

    override fun create(cmd: AfterSaleCreateCMD) = write { delegate.create(cmd) }

    override fun approve(cmd: AfterSaleApproveCMD) = write { delegate.approve(cmd) }

    override fun reject(cmd: AfterSaleRejectCMD) = write { delegate.reject(cmd) }

    override fun cancel(cmd: AfterSaleCancelCMD) = write { delegate.cancel(cmd) }

    override fun receiveReturn(cmd: AfterSaleReceiveReturnCMD) =
        write { delegate.receiveReturn(cmd) }

    override fun retryRefund(cmd: AfterSaleRetryRefundCMD) = write { delegate.retryRefund(cmd) }

    override fun recordRefundSucceeded(
        afterSaleId: AfterSaleId,
        refundId: String,
        occurredAt: Instant,
    ) = write { delegate.recordRefundSucceeded(afterSaleId, refundId, occurredAt) }

    override fun recordRefundFailed(
        afterSaleId: AfterSaleId,
        refundId: String,
        reason: String,
        occurredAt: Instant,
    ) = write { delegate.recordRefundFailed(afterSaleId, refundId, reason, occurredAt) }

    private fun <T> read(block: () -> T): T = requireNotNull(read.execute { block() })

    private fun <T> write(block: () -> T): T = requireNotNull(write.execute { block() })
}
