package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.order.config.TestBeanConfig

class MockSaleOrderEventPublisher : SaleOrderEventPublisher {

    override fun publish(event: DomainEvent) {
        TestBeanConfig.getSimpleDomainEventRegistry().publish(event)
    }
}