package com.jstore.com.jstore.order.acl.freight

import com.jstore.order.acl.FreightService
import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.properties.FreightBill
import org.springframework.stereotype.Service


@Service
class MockFreightService : FreightService {
    override fun delivery(saleOrder: SaleOrder) {
        TODO("Not yet implemented")
    }

    override fun queryByIds(ids: Collection<String>): List<FreightBill> {
        TODO("Not yet implemented")
    }
}