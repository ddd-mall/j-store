package com.jstore.order.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventPublisher
import com.jstore.common.framework.DomainEventRegistry
import org.springframework.stereotype.Component

interface SaleOrderEventPublisher : DomainEventPublisher

@Component
class SaleOrderEventPublisherImpl(
    private val domainEventRegistry: DomainEventRegistry
) : SaleOrderEventPublisher {

    override fun publish(event: DomainEvent) {
        domainEventRegistry.publish(event)
    }
}