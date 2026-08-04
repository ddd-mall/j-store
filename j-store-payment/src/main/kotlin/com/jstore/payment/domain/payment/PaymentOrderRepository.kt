package com.jstore.payment.domain.payment

import com.jstore.common.framework.Repository

interface PaymentOrderRepository : Repository<PaymentOrderId, PaymentOrder> {
    fun findByOrderId(orderId: Long): PaymentOrder?
    fun findByRefundId(refundId: PaymentRefundId): PaymentOrder?
}
