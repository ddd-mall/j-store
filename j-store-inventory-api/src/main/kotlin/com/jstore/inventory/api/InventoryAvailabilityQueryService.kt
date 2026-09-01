package com.jstore.inventory.api

data class InventoryAvailabilityKey(val skuId: Long, val fulfillmentNodeId: String)

data class InventoryAvailabilityInfo(
    val skuId: Long,
    val fulfillmentNodeId: String,
    val availableToPromise: Int,
    val sourceVersion: Long,
    val availabilityVersion: Long,
)

fun interface InventoryAvailabilityQueryService {
    fun queryAvailability(keys: List<InventoryAvailabilityKey>): List<InventoryAvailabilityInfo>
}
