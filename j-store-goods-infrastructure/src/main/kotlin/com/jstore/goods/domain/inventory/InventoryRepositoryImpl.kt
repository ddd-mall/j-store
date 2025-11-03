package com.jstore.goods.domain.inventory

import com.jstore.goods.domain.inventory.persistence.InventoryPO
import com.jstore.goods.domain.inventory.persistence.InventoryPOJpaRepository
import org.springframework.stereotype.Repository

/**
 * 商品库存Repository实现
 */
@Repository
class InventoryRepositoryImpl(
    private val inventoryPOJpaRepository: InventoryPOJpaRepository
) : InventoryRepository {

    override fun findById(id: CommodityCode): Inventory? {
        val inventoryPO = inventoryPOJpaRepository.findByCommodityCode(id.value)
        return inventoryPO?.toDomain()
    }

    override fun save(entity: Inventory): Inventory {
        if (entity !is InventoryImpl) {
            throw IllegalArgumentException("Invalid inventory type")
        }

        val po = inventoryPOJpaRepository.findByCommodityCode(entity.id.value)
            ?: InventoryPO(commodityCode = entity.id.value)

        po.apply {
            // 使用反射或者添加getter方法来获取私有字段
            // 这里简化处理，实际项目中可能需要在InventoryImpl中添加公开的getter
            availableQuantity = getAvailableQuantity(entity)
            reservedQuantity = getReservedQuantity(entity)
        }

        val savedPO = inventoryPOJpaRepository.save(po)
        return savedPO.toDomain()
    }

    private fun InventoryPO.toDomain(): Inventory {
        return InventoryImpl(
            id = CommodityCode(this.commodityCode),
            availableQuantity = this.availableQuantity,
            reservedQuantity = this.reservedQuantity,
            version = this.version
        )
    }

    // 辅助方法：通过反射获取私有字段值
    private fun getAvailableQuantity(inventory: InventoryImpl): java.math.BigDecimal {
        val field = InventoryImpl::class.java.getDeclaredField("availableQuantity")
        field.isAccessible = true
        return field.get(inventory) as java.math.BigDecimal
    }

    private fun getReservedQuantity(inventory: InventoryImpl): java.math.BigDecimal {
        val field = InventoryImpl::class.java.getDeclaredField("reservedQuantity")
        field.isAccessible = true
        return field.get(inventory) as java.math.BigDecimal
    }
}

