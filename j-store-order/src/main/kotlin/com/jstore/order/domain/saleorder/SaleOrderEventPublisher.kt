package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEventPublisher
import com.jstore.common.framework.DomainEventRegistry
import org.springframework.stereotype.Component

interface SaleOrderEventPublisher : DomainEventPublisher

@Component
class SaleOrderEventPublisherImpl(
    private var domainEventRegistry: DomainEventRegistry
) : SaleOrderEventPublisher {

    override fun publishEvent(event: Any) {
        domainEventRegistry.publishEvent(event)
    }
}