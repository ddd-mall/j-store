package com.jstore.order.domain.saleorder

import com.jstore.order.config.TestBeanConfig.domainEventRegistry

class MockSaleOrderEventPublisher : SaleOrderEventPublisher {

    override fun publishEvent(event: Any) {
        domainEventRegistry.publishEvent(event)
    }
}