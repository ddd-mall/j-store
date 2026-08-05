package com.jstore.fulfillment.config

import com.jstore.fulfillment.service.FulfillmentRequest
import com.jstore.fulfillment.service.FulfillmentUseCase
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalFulfillmentUseCase(
    private val delegate: FulfillmentUseCase,
    transactionManager: PlatformTransactionManager,
) : FulfillmentUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun createForOrder(request: FulfillmentRequest) = tx {
        delegate.createForOrder(request)
    }

    override fun getByOrderId(orderId: Long) = query { delegate.getByOrderId(orderId) }

    override fun prepare(orderId: Long, occurredAt: Instant) = tx {
        delegate.prepare(orderId, occurredAt)
    }

    override fun dispatch(
        orderId: Long,
        carrierCode: String,
        trackingNumber: String,
        occurredAt: Instant,
    ) = tx { delegate.dispatch(orderId, carrierCode, trackingNumber, occurredAt) }

    override fun deliver(orderId: Long, occurredAt: Instant) = tx {
        delegate.deliver(orderId, occurredAt)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })
}
