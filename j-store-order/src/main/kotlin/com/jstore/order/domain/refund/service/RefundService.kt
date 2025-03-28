package com.jstore.order.domain.refund.service

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.domain.refund.RefundOrder
import com.jstore.order.domain.refund.RefundOrderRepository
import com.jstore.order.domain.refund.RefundType
import com.jstore.order.domain.saleorder.SaleOrderId
import com.jstore.common.properties.Price
import com.jstore.order.domain.refund.RefundOrderId
import org.springframework.stereotype.Service

@Service
class RefundService(
    private val refundOrderRepository: RefundOrderRepository,
    private val snowFlakSequence: SnowFlakSequence
) {

    fun createRefund(
        saleOrderId: SaleOrderId,
        refundType: RefundType,
        reason: String?,
        amount: Price
    ): RefundOrder {
        val refundOrder = RefundOrder(
            RefundOrderId(snowFlakSequence.nextId()),
            refundType,
            saleOrderId,
            reason,
            amount
        )
        refundOrderRepository.save(refundOrder)
        return refundOrder
    }
}