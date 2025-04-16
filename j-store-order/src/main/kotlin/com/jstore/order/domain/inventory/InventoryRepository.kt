package com.jstore.order.domain.inventory

import com.jstore.common.framework.Repository
import com.jstore.order.domain.acl.GoodsId
import com.jstore.order.domain.order.OrderId

interface InventoryRepository : Repository<InventoryId, Inventory> {
    fun findAllByOrderId(orderId: OrderId): List<Inventory>
    fun findByOrderIdAndGoodsId(orderId: OrderId, goodsId: GoodsId): Inventory?
    fun saveAll(inventories: Collection<Inventory>) : List<Inventory>
}