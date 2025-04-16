package com.jstore.order.domain.inventory.command

import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.domain.inventory.InventoryFactory
import com.jstore.order.domain.inventory.InventoryRepository
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.acl.GoodsId
import org.springframework.stereotype.Component
import java.math.BigDecimal

class InventoryCreateCMD(
    val orderId: OrderId,
    val goodsId: GoodsId,
    val quantity: BigDecimal
)

@Component
class InventoryCreateCMDHandler(
    private val inventoryFactory: InventoryFactory,
    private val inventoryRepository: InventoryRepository
) {
    fun handle(inventoryCreateCMD: InventoryCreateCMD): Inventory {
        val inventory = inventoryFactory.create(inventoryCreateCMD)
        return inventoryRepository.save(inventory)
    }
}