package com.jstore.com.jstore.order.domain.inventory

import com.jstore.com.jstore.order.domain.inventory.persistent.InventoryPO
import com.jstore.com.jstore.order.domain.inventory.persistent.InventoryPOJpaRepository
import com.jstore.order.domain.inventory.Inventory
import com.jstore.order.domain.inventory.InventoryId
import com.jstore.order.domain.inventory.InventoryRepository
import com.jstore.order.domain.saleorder.SaleOrderId
import com.jstore.order.service.acl.GoodsId
import org.springframework.stereotype.Repository

@Repository
class InventoryRepositoryImpl(
    private val inventoryPOJpaRepository: InventoryPOJpaRepository,
) : InventoryRepository {
    override fun findAllByOrderId(orderId: SaleOrderId): List<Inventory> {
        return inventoryPOJpaRepository.findAllByOrderId(orderId.value).map {
            it.toInventory()
        }
    }

    override fun findByOrderIdAndGoodsId(orderId: SaleOrderId, goodsId: GoodsId): Inventory? {
        return inventoryPOJpaRepository.findStockPOByOrderIdAndSpuIdAndSkuId(orderId.value, goodsId.skuId, goodsId.spuId)
            ?.toInventory()
    }

    override fun saveAll(inventories: Collection<Inventory>) : List<Inventory> {
        val poList = inventories.map { InventoryPO(it) }.toList()
        return inventoryPOJpaRepository.saveAll(poList).map { it.toInventory() }
    }

    override fun save(entity: Inventory): Inventory {
        val po = InventoryPO(entity)
        return inventoryPOJpaRepository.save(po).toInventory()
    }

    override fun findById(id: InventoryId): Inventory? {
        return inventoryPOJpaRepository.findById(id.value).map { it.toInventory() }.orElse(null)
    }
}