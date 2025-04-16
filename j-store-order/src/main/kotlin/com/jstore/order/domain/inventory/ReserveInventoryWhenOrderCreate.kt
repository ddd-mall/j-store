package com.jstore.order.domain.inventory

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.order.domain.acl.OuterInventoryServiceACL
import com.jstore.order.domain.inventory.command.InventoryCreateCMD
import com.jstore.order.domain.inventory.command.InventoryCreateCMDHandler
import com.jstore.order.domain.order.OrderCreatedEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItem
import org.springframework.stereotype.Component

@Component
class ReserveInventoryWhenOrderCreate(
    private val inventoryCreateCMDHandler: InventoryCreateCMDHandler,
    private val outerInventoryServiceACL: OuterInventoryServiceACL,
    private val inventoryRepository: InventoryRepository
) : DomainEventListener<OrderCreatedEvent> {

    override fun onDomainEvent(event: DomainEvent) {
        when (event) {
            is OrderCreatedEvent -> {
                event.order.orderItems
                    .map { newInventoryCreateCMD(event.order.id, it) }
                    .map(inventoryCreateCMDHandler::handle)
                    .map{
                        outerInventoryServiceACL.reserve(it)
                        inventoryRepository.save(it)
                    }

            }
        }
    }

    private fun newInventoryCreateCMD(orderId: OrderId, orderItem: OrderItem): InventoryCreateCMD {
        return InventoryCreateCMD(
            orderId = orderId,
            goodsId = orderItem.goodsId,
            quantity = orderItem.quantity,
        )
    }
}
