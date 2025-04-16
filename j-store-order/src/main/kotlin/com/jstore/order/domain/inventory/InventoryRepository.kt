package com.jstore.order.domain.inventory

import com.jstore.common.framework.Repository
import com.jstore.order.service.acl.GoodsId
import com.jstore.order.domain.saleorder.SaleOrderId

interface InventoryRepository : Repository<InventoryId, Inventory> {
    fun findAllByOrderId(orderId: SaleOrderId): List<Inventory>
    fun findByOrderIdAndGoodsId(orderId: SaleOrderId, goodsId: GoodsId): Inventory?
    fun saveAll(inventories: Collection<Inventory>) : List<Inventory>
}