package com.jstore.order.domain.inventory.command

import com.jstore.common.errors.CommonErrors.OBJECT_NOT_FOUND
import com.jstore.order.acl.OuterInventoryServiceACL
import com.jstore.order.domain.inventory.InventoryId
import com.jstore.order.domain.inventory.InventoryRepository
import org.springframework.stereotype.Service

class ReserveInventoryCMD(
    val inventoryId: InventoryId,
)


@Service
class ReserveInventoryCMDHandler(
    private val inventoryRepository: InventoryRepository,
    private val outerInventoryServiceACL: OuterInventoryServiceACL
) {
    fun handle(command: ReserveInventoryCMD) {
        val inventory = inventoryRepository.findById(command.inventoryId) ?: throw OBJECT_NOT_FOUND
        outerInventoryServiceACL.reserve(inventory)
        inventory.reserve()
        inventoryRepository.save(inventory)
    }
}