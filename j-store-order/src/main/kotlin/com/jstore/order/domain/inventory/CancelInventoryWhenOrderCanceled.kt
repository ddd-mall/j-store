package com.jstore.order.domain.inventory

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.order.domain.inventory.command.CancelAllInventoryOfCMD
import com.jstore.order.domain.inventory.command.CancelInventoryHandler
import com.jstore.order.domain.order.event.OrderCanceledEvent
import org.springframework.stereotype.Component

@Component
class CancelInventoryWhenOrderCanceled(
    private val cancelInventoryHandler: CancelInventoryHandler,
) : DomainEventListener<OrderCanceledEvent> {
    override fun onDomainEvent(event: DomainEvent) {
        when (event) {
            is OrderCanceledEvent -> cancelInventoryHandler.handle(CancelAllInventoryOfCMD(event.orderId))
        }
    }
}