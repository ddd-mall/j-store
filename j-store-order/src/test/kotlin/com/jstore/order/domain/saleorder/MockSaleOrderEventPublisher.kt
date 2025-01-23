package com.jstore.order.domain.saleorder

import org.springframework.context.ApplicationEventPublisher

class MockSaleOrderEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher
) : SaleOrderEventPublisher {

    override fun publishEvent(event: Any) {
        return applicationEventPublisher.publishEvent(event)
    }
}