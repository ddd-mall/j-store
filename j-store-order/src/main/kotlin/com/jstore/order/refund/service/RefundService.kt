package com.jstore.com.jstore.order.refund.service

import com.jstore.com.jstore.order.refund.RefundOrder
import com.jstore.com.jstore.order.refund.RefundOrderRepository
import com.jstore.com.jstore.order.refund.RefundType
import com.jstore.order.saleorder.SaleOrderId
import com.jstore.order.saleorder.properties.Price
import org.springframework.stereotype.Service

@Service
class RefundService(private val refundOrderRepository: RefundOrderRepository) {

    fun createRefund(
        saleOrderId: SaleOrderId,
        refundType: RefundType,
        reason: String?,
        amount: Price
    ):RefundOrder {
        val refundOrder = RefundOrder(
            null,
            refundType,
            saleOrderId,
            reason,
            amount
        )
        refundOrderRepository.save(refundOrder)
        return refundOrder
    }
}