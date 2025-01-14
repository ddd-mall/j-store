package com.jstore.order.domain.refund.service

import com.jstore.order.domain.refund.RefundOrder
import com.jstore.order.domain.refund.RefundOrderRepository
import com.jstore.order.domain.refund.RefundType
import com.jstore.order.domain.saleorder.SaleOrderId
import com.jstore.common.properties.Price
import org.springframework.stereotype.Service

@Service
class RefundService(private val refundOrderRepository: RefundOrderRepository) {

    fun createRefund(
        saleOrderId: SaleOrderId,
        refundType: RefundType,
        reason: String?,
        amount: Price
    ): RefundOrder {
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