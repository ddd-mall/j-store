package com.jstore.order.domain.inventory

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.domain.inventory.command.CreateInventoryCMD
import org.springframework.stereotype.Component


@Component
class InventoryFactory(
    private val snowFlakSequence: SnowFlakSequence,
) {
    fun create(createInventoryCMD: CreateInventoryCMD): Inventory {
        return Inventory(
            id = InventoryId(snowFlakSequence.nextId()),
            orderId = createInventoryCMD.orderId,
            goodsId = createInventoryCMD.goodsId,
            quantity = createInventoryCMD.quantity,
            status = InventoryStatus.CREATED
        )
    }
}