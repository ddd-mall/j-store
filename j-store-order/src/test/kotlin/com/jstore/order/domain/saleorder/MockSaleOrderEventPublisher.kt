package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.order.config.TestBeanConfig.domainEventRegistry

class MockSaleOrderEventPublisher : SaleOrderEventPublisher {

    override fun publish(event: DomainEvent) {
        domainEventRegistry.publish(event)
    }
}