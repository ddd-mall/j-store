package com.jstore.order.service

import com.jstore.order.domain.inventory.InventoryFactory
import com.jstore.order.domain.inventory.InventoryRepository
import com.jstore.order.domain.order.Order
import com.jstore.order.service.acl.OuterInventoryServiceACL
import org.springframework.stereotype.Service

@Service
class InventoryService(
    private val inventoryFactory: InventoryFactory,
    private val inventoryRepository: InventoryRepository,
    private val outerInventoryServiceACL: OuterInventoryServiceACL,
) {
    fun createAndReserve(order: Order) {
        val inventories = inventoryFactory.create(order)
        outerInventoryServiceACL.reserveAll(inventories)
        inventoryRepository.saveAll(inventories)
    }
}