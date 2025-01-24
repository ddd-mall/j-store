package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventRegistry

class MockSaleOrderEventPublisher(
    private val applicationEventPublisher: DomainEventRegistry
) : SaleOrderEventPublisher {

    override fun publishEvent(event: DomainEvent) {
        return applicationEventPublisher.publishEvent(event)
    }
}