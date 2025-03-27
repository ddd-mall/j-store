package com.jstore.goods.domain.inventory

import org.springframework.stereotype.Component

@Component
interface InventoryFactory {
    fun create(createCMD: StorageCreateCMD): Inventory {
        return InventoryImpl(
            id = createCMD.commodityCode,
            availableQuantity = createCMD.quantity,
        )
    }
}
