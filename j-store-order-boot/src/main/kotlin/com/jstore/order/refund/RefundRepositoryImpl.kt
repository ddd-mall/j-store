package com.jstore.com.jstore.order.refund

import com.jstore.order.refund.RefundOrder
import com.jstore.order.refund.RefundOrderId
import com.jstore.order.refund.RefundOrderRepository
import org.springframework.stereotype.Repository

@Repository
class RefundRepositoryImpl: RefundOrderRepository {
    override fun save(entity: RefundOrder): RefundOrder {
        TODO("Not yet implemented")
    }

    override fun findById(id: RefundOrderId): RefundOrder? {
        TODO("Not yet implemented")
    }
}