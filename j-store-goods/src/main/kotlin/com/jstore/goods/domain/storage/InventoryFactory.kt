package com.jstore.goods.domain.storage

import org.springframework.stereotype.Component

@Component
interface InventoryFactory {
    fun create(createCMD: StorageCreateCMD): Inventory {
        TODO()
    }
}
