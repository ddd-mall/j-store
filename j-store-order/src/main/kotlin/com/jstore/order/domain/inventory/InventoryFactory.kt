package com.jstore.order.domain.inventory

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.domain.inventory.command.InventoryCreateCMD
import org.springframework.stereotype.Component


@Component
class InventoryFactory(
    private val snowFlakSequence: SnowFlakSequence,
) {
    fun create(inventoryCreateCMD: InventoryCreateCMD): Inventory {
        return Inventory(
            id = InventoryId(snowFlakSequence.nextId()),
            orderId = inventoryCreateCMD.orderId,
            goodsId = inventoryCreateCMD.goodsId,
            quantity = inventoryCreateCMD.quantity,
            status = InventoryStatus.CREATED
        )
    }
}