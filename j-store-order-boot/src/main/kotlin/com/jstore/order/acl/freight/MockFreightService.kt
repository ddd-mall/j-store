package com.jstore.com.jstore.order.acl.freight

import com.jstore.order.service.acl.FreightService
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.FreightBill
import org.springframework.stereotype.Service


@Service
class MockFreightService : FreightService {
    override fun delivery(order: Order) {
        TODO("Not yet implemented")
    }

    override fun queryByIds(ids: Collection<String>): List<FreightBill> {
        TODO("Not yet implemented")
    }
}