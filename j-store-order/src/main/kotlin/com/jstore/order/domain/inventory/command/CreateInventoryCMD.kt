package com.jstore.order.domain.inventory.command

import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.domain.inventory.InventoryFactory
import com.jstore.order.domain.inventory.InventoryRepository
import com.jstore.order.domain.order.OrderId
import com.jstore.order.acl.GoodsId
import org.springframework.stereotype.Component
import java.math.BigDecimal

class CreateInventoryCMD(
    val orderId: OrderId,
    val goodsId: GoodsId,
    val quantity: BigDecimal
)

@Component
class InventoryCreateCMDHandler(
    private val inventoryFactory: InventoryFactory,
    private val inventoryRepository: InventoryRepository
) {
    fun handle(createInventoryCMD: CreateInventoryCMD): Inventory {
        val inventory = inventoryFactory.create(createInventoryCMD)
        return inventoryRepository.save(inventory)
    }
}