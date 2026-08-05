package com.jstore.payment.config

import com.jstore.payment.domain.payment.PaymentRefundId
import com.jstore.payment.service.PaymentCaptureCommand
import com.jstore.payment.service.PaymentOrderRequest
import com.jstore.payment.service.PaymentRefundRequest
import com.jstore.payment.service.PaymentUseCase
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalPaymentUseCase(
    private val delegate: PaymentUseCase,
    transactionManager: PlatformTransactionManager,
) : PaymentUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun createForOrder(request: PaymentOrderRequest) = tx {
        delegate.createForOrder(request)
    }

    override fun getByOrderId(orderId: Long) = query { delegate.getByOrderId(orderId) }

    override fun getByRefundId(refundId: PaymentRefundId) = query {
        delegate.getByRefundId(refundId)
    }

    override fun capture(command: PaymentCaptureCommand, occurredAt: Instant) = tx {
        delegate.capture(command, occurredAt)
    }

    override fun requestRefund(request: PaymentRefundRequest, occurredAt: Instant) = tx {
        delegate.requestRefund(request, occurredAt)
    }

    override fun retryRefund(refundId: PaymentRefundId, occurredAt: Instant) = tx {
        delegate.retryRefund(refundId, occurredAt)
    }

    override fun markRefundSucceeded(
        refundId: PaymentRefundId,
        providerRefundId: String,
        occurredAt: Instant,
    ) = tx { delegate.markRefundSucceeded(refundId, providerRefundId, occurredAt) }

    override fun markRefundFailed(refundId: PaymentRefundId, reason: String, occurredAt: Instant) =
        tx {
            delegate.markRefundFailed(refundId, reason, occurredAt)
        }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })
}
