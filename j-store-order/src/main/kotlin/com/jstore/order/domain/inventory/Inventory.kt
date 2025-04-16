package com.jstore.order.domain.inventory


import com.jstore.common.framework.Entity
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.Id
import com.jstore.order.domain.order.OrderId
import com.jstore.order.service.acl.GoodsId
import java.math.BigDecimal


data class InventoryId(override val value: String) : Id<String>(value)

class Inventory(
    override val id: InventoryId,
    val orderId: OrderId,
    val goodsId: GoodsId,
    val quantity: BigDecimal,
    var inventoryStatus: InventoryStatus,
    var outerInventoryId: String = "",
) : Entity<InventoryId> {
    private val log: Logger = LoggerFactory.getLogger(this::class)


    fun reserve() {
        if (inventoryStatus != InventoryStatus.CREATED) {
            throw InventoryErrors.IllegalState
        }

        inventoryStatus = InventoryStatus.RESERVED

        log.info("stock $id has been reserve success, goodsId: $goodsId, quantity: $quantity")
    }


    fun confirm() {
        if (inventoryStatus != InventoryStatus.RESERVED) {
            throw InventoryErrors.IllegalState
        }

        inventoryStatus = InventoryStatus.CONFIRMED
    }


    fun cancel() {

    }
}

enum class InventoryStatus {
    CREATED, RESERVED, CONFIRMED
}
