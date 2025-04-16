package com.jstore.order.domain.inventory.command

import com.jstore.order.acl.OuterInventoryServiceACL
import com.jstore.order.domain.inventory.InventoryRepository
import com.jstore.order.domain.order.OrderId
import org.springframework.stereotype.Service

class CancelAllInventoryOfCMD(
    val orderId: OrderId,
)


@Service
class CancelInventoryHandler(
    private val inventoryRepository: InventoryRepository,
    private val outerInventoryServiceACL: OuterInventoryServiceACL
) {
    fun handle(cmd: CancelAllInventoryOfCMD) {
        val inventories = inventoryRepository.findAllByOrderId(orderId = cmd.orderId)
        outerInventoryServiceACL.cancelAll(inventories)
        inventories.forEach { inventory -> inventory.cancel() }
        inventoryRepository.saveAll(inventories)
    }
}