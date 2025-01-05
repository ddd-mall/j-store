package com.jstore.com.jstore.order.saleorder

import com.jstore.common.framework.DomainEvent
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.saleorder.SaleOrderEventPublisher
import org.springframework.stereotype.Component

@Component
class SimpleSaleOrderEventPublisher: SaleOrderEventPublisher {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun publish(event: DomainEvent) {
        log.info("$event")
    }
}