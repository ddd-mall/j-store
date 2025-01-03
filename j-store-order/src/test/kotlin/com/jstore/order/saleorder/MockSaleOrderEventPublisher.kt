package com.jstore.order.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.SimpleDomainEventRegistrySingleToneFactory

class MockSaleOrderEventPublisher : SaleOrderEventPublisher {

    override fun publish(event: DomainEvent) {
        SimpleDomainEventRegistrySingleToneFactory.get().publish(event)
    }
}