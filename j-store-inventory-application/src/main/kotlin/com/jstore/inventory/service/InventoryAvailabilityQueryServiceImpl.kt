package com.jstore.inventory.service

import com.jstore.inventory.api.InventoryAvailabilityInfo
import com.jstore.inventory.api.InventoryAvailabilityKey
import com.jstore.inventory.api.InventoryAvailabilityQueryService
import com.jstore.inventory.domain.FulfillmentNodeId
import com.jstore.inventory.domain.SkuId
import com.jstore.inventory.domain.StockPositionRepository

class InventoryAvailabilityQueryServiceImpl(private val positions: StockPositionRepository) : InventoryAvailabilityQueryService {
    override fun queryAvailability(keys: List<InventoryAvailabilityKey>): List<InventoryAvailabilityInfo> =
        keys.distinct().mapNotNull { key ->
            positions.findBySkuAndNode(SkuId(key.skuId), FulfillmentNodeId(key.fulfillmentNodeId))?.let {
                InventoryAvailabilityInfo(key.skuId, key.fulfillmentNodeId, it.availableToPromise, it.sourceVersion, it.persistenceVersion)
            }
        }
}
