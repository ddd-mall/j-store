package com.jstore.payment.domain.payment

import com.jstore.common.framework.AggregateRepository

interface PaymentOrderRepository : AggregateRepository<PaymentOrderId, PaymentOrder> {
    fun findByOrderId(orderId: Long): PaymentOrder?

    fun findByRefundId(refundId: PaymentRefundId): PaymentOrder?
}
