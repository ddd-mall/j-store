package com.jstore.com.jstore.order.refund

import org.springframework.stereotype.Repository

@Repository
open class RefundRepositoryImpl: RefundOrderRepository {
    override fun save(entity: RefundOrder): RefundOrder {
        TODO("Not yet implemented")
    }

    override fun findById(id: RefundOrderId): RefundOrder? {
        TODO("Not yet implemented")
    }
}