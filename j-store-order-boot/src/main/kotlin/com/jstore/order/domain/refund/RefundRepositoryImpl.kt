package com.jstore.com.jstore.order.domain.refund

import com.jstore.order.domain.refund.RefundOrder
import com.jstore.order.domain.refund.RefundOrderId
import com.jstore.order.domain.refund.RefundOrderRepository
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