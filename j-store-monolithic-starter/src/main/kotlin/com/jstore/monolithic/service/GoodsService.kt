package com.jstore.monolithic.service

import com.jstore.goods.domain.commodity.persistence.SpuPO
import com.jstore.goods.domain.commodity.persistence.SpuPOJpaRepository
import com.jstore.goods.domain.inventory.persistence.InventoryPO
import com.jstore.goods.domain.inventory.persistence.InventoryPOJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 商品服务 - 使用商品数据源
 */
@Service
class GoodsService(
    private val inventoryRepository: InventoryPOJpaRepository,
    private val spuRepository: SpuPOJpaRepository
) {

    /**
     * 创建商品库存
     * 使用商品数据库事务
     */
    @Transactional("goodsTransactionManager")
    fun createInventory(commodityCode: Long, quantity: BigDecimal): InventoryPO {
        val inventory = InventoryPO(
            commodityCode = commodityCode,
            availableQuantity = quantity,
            reservedQuantity = BigDecimal.ZERO
        )
        return inventoryRepository.save(inventory)
    }

    /**
     * 创建SPU
     */
    @Transactional("goodsTransactionManager")
    fun createSpu(spuId: Long, name: String, status: String): SpuPO {
        val spu = SpuPO(
            spuId = spuId,
            spuName = name,
            status = status
        )
        return spuRepository.save(spu)
    }

    /**
     * 查询所有库存
     */
    @Transactional("goodsTransactionManager", readOnly = true)
    fun getAllInventory(): List<InventoryPO> {
        return inventoryRepository.findAll()
    }

    /**
     * 查询所有SPU
     */
    @Transactional("goodsTransactionManager", readOnly = true)
    fun getAllSpus(): List<SpuPO> {
        return spuRepository.findAll()
    }
}

