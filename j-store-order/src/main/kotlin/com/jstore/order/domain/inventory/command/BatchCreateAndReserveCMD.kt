package com.jstore.order.domain.inventory.command

import com.jstore.order.acl.OuterInventoryServiceACL
import com.jstore.order.domain.inventory.InventoryFactory
import com.jstore.order.domain.inventory.InventoryRepository
import com.jstore.order.domain.order.Order
import org.springframework.stereotype.Service

class BatchCreateAndReserveCMD(
    val order: Order,
)


@Service
class BatchCreateAndReserveHandler(
    private val inventoryFactory: InventoryFactory,
    private val outerInventoryServiceACL: OuterInventoryServiceACL,
    private val inventoryRepository: InventoryRepository,
) {

    fun handle(cmd: BatchCreateAndReserveCMD) {
        val inventories = getInventoryBatchCreateCMD(cmd.order).map(inventoryFactory::create)
        outerInventoryServiceACL.reserveAll(inventories)
        inventories.forEach { inventory -> inventory.reserve() }
        inventoryRepository.saveAll(inventories)
    }

    private fun getInventoryBatchCreateCMD(order: Order): List<CreateInventoryCMD> {
        return order.orderItems.map { orderItem ->
            CreateInventoryCMD(
                order.id,
                orderItem.goodsId,
                orderItem.quantity
            )
        }
    }
}