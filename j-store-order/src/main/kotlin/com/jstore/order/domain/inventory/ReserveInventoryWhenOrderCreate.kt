package com.jstore.order.domain.inventory

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.order.domain.inventory.command.BatchCreateAndReserveCMD
import com.jstore.order.domain.inventory.command.BatchCreateAndReserveHandler
import com.jstore.order.domain.order.event.OrderCreatedEvent
import org.springframework.stereotype.Component

@Component
class ReserveInventoryWhenOrderCreate(
    private val batchCreateAndReserveHandler: BatchCreateAndReserveHandler,
) : DomainEventListener<OrderCreatedEvent> {

    override fun onDomainEvent(event: DomainEvent) {
        when (event) {
            is OrderCreatedEvent -> {
                batchCreateAndReserveHandler.handle(BatchCreateAndReserveCMD(event.order))
            }
        }
    }
}
