package com.jstore.common.framework.event.outbox

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

interface OutboxRelayTransactionOperations {
    fun <T> executeDelivery(action: () -> T): T

    fun <T> executeFailure(action: () -> T): T
}

object ImmediateOutboxRelayTransactionOperations : OutboxRelayTransactionOperations {
    override fun <T> executeDelivery(action: () -> T): T = action()

    override fun <T> executeFailure(action: () -> T): T = action()
}

class SpringOutboxRelayTransactionOperations(
    transactionManager: PlatformTransactionManager,
) : OutboxRelayTransactionOperations {
    private val deliveryTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
    }
    private val failureTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    override fun <T> executeDelivery(action: () -> T): T {
        return deliveryTransaction.execute { action() }
            ?: throw IllegalStateException("Outbox delivery transaction returned null")
    }

    override fun <T> executeFailure(action: () -> T): T {
        return failureTransaction.execute { action() }
            ?: throw IllegalStateException("Outbox failure transaction returned null")
    }
}
