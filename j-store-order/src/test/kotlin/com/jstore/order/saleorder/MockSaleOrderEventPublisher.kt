package com.jstore.order.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.SimpleDomainEventRegistry

class MockSaleOrderEventPublisher : SaleOrderEventPublisher {

    override fun publish(event: DomainEvent) {
        SimpleDomainEventRegistry.getDefaultInstance().publish(event)
    }
}