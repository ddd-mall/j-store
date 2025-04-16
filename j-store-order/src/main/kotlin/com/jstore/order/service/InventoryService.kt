package com.jstore.order.service

import com.jstore.order.domain.inventory.InventoryFactory
import com.jstore.order.domain.inventory.InventoryRepository
import com.jstore.order.domain.saleorder.SaleOrder
import com.jstore.order.service.acl.OuterInventoryServiceACL
import org.springframework.stereotype.Service

@Service
class InventoryService(
    private val inventoryFactory: InventoryFactory,
    private val inventoryRepository: InventoryRepository,
    private val outerInventoryServiceACL: OuterInventoryServiceACL,
) {
    fun createAndReserve(saleOrder: SaleOrder) {
        val inventories = inventoryFactory.create(saleOrder)
        outerInventoryServiceACL.reserveAll(inventories)
        inventoryRepository.saveAll(inventories)
    }
}