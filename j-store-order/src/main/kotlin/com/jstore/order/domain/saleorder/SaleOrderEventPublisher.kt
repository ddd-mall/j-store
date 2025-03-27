package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventPublisher
import com.jstore.common.framework.DomainEventRegistry
import org.springframework.stereotype.Component

interface SaleOrderEventPublisher : DomainEventPublisher

@Component
class SaleOrderEventPublisherImpl(
    private val eventRegistry: DomainEventRegistry
) : SaleOrderEventPublisher {

    override fun <T : DomainEvent> publishEvent(event: T) {
        eventRegistry.publishEvent(event)
    }
}