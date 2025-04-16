package com.jstore.order.domain.inventory

import com.jstore.order.domain.acl.GoodsId
import com.jstore.order.config.TestBeanConfig.snowFlakSequence
import com.jstore.order.domain.order.OrderId
import com.jstore.order.framwork.AbstractMockRepository

class MockInventoryRepositoryImpl : InventoryRepository, AbstractMockRepository<InventoryId, Inventory>() {


    override fun findAllByOrderId(orderId: OrderId): List<Inventory> {
        return super.objList.filter { it.orderId == orderId }
    }

    override fun findByOrderIdAndGoodsId(orderId: OrderId, goodsId: GoodsId): Inventory? {
        return super.objList.find { it.orderId == orderId && it.goodsId == goodsId }
    }

    override fun saveAll(inventories: Collection<Inventory>): List<Inventory> {
        return inventories.map(::save)
    }

    override fun nextId(): InventoryId {
        return InventoryId(snowFlakSequence.nextId())
    }

    override fun copyAnEntity(nextId: InventoryId, entity: Inventory): Inventory {
        return Inventory(
            id = nextId,
            orderId = entity.orderId,
            goodsId = entity.goodsId,
            quantity = entity.quantity,
            status = entity.status,
        )
    }
}