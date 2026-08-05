package com.jstore.goods.domain.inventory

/** 库存工厂 领域层接口，不依赖任何框架注解 Bean 注册由 boot 模块的配置类负责 */
interface InventoryFactory {
    fun create(createCMD: StorageCreateCMD): Inventory {
        return InventoryImpl(
            id = createCMD.commodityCode,
            availableQuantity = createCMD.quantity,
        )
    }
}
