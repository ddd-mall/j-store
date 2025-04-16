package com.jstore.order.domain.inventory

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.domain.saleorder.SaleOrder
import org.springframework.stereotype.Component


@Component
class InventoryFactory(
    private val snowFlakSequence: SnowFlakSequence,
) {
    fun create(salOrder: SaleOrder): List<Inventory> {
        return salOrder.orderItems.map { orderItem ->
            Inventory(
                id = InventoryId(snowFlakSequence.nextId().toString()),
                orderId = salOrder.id,
                goodsId = orderItem.goodsId,
                quantity = orderItem.quantity,
                inventoryStatus = InventoryStatus.CREATED
            )
        }.toList()
    }
}