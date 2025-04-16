package com.jstore.order.domain.inventory

import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.order.domain.inventory.command.CreateInventoryCMD
import com.jstore.order.domain.inventory.command.InventoryCreateCMDHandler
import com.jstore.order.domain.inventory.command.ReserveInventoryCMD
import com.jstore.order.domain.inventory.command.ReserveInventoryCMDHandler
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItem
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.event.OrderCreatedEvent
import org.springframework.stereotype.Component

@Component
class ReserveInventoryWhenOrderCreate(
    private val inventoryCreateCMDHandler: InventoryCreateCMDHandler,
    private val reserveInventoryCMDHandler: ReserveInventoryCMDHandler,
    private val orderRepository: OrderRepository,
) : DomainEventListener<OrderCreatedEvent> {

    override fun onDomainEvent(event: DomainEvent) {
        when (event) {
            is OrderCreatedEvent -> {
                val order = orderRepository.findById(event.orderId) ?: throw CommonErrors.OBJECT_NOT_FOUND
                order.orderItems
                    .map { newInventoryCreateCMD(order.id, it) }
                    .map(inventoryCreateCMDHandler::handle)
                    .map(::newReserveInventoryCMD)
                    .map(reserveInventoryCMDHandler::handle)
            }
        }
    }

    private fun newInventoryCreateCMD(orderId: OrderId, orderItem: OrderItem): CreateInventoryCMD {
        return CreateInventoryCMD(
            orderId = orderId,
            goodsId = orderItem.goodsId,
            quantity = orderItem.quantity,
        )
    }

    private fun newReserveInventoryCMD(inventory: Inventory): ReserveInventoryCMD {
        return ReserveInventoryCMD(
            inventoryId = inventory.id,
        )
    }
}
