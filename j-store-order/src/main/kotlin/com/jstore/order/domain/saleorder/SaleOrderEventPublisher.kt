package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

interface SaleOrderEventPublisher : DomainEventPublisher<DomainEvent>

@Component
class SaleOrderEventPublisherImpl(
    private val applicationEventPublisher: ApplicationEventPublisher
) : SaleOrderEventPublisher {

    override fun publishEvent(event: DomainEvent) {
        applicationEventPublisher.publishEvent(event)
    }
}