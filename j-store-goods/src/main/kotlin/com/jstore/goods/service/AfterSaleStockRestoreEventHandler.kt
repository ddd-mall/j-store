package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.utils.onFailure
import com.jstore.goods.acl.event.AfterSaleStockRestoreRequestedEvent
import com.jstore.goods.domain.inventory.CommodityCode
import java.math.BigDecimal

class AfterSaleStockRestoreEventHandler(
    private val inventoryServiceProvider: () -> InventoryService?
) : DomainEventListener<AfterSaleStockRestoreRequestedEvent> {
    constructor(inventoryService: InventoryService) : this({ inventoryService })

    override fun listenerId() = "goods.after-sale-stock-restore.v1"

    override fun onDomainEvent(event: AfterSaleStockRestoreRequestedEvent) {
        val inventoryService =
            inventoryServiceProvider()
                ?: throw IllegalStateException("inventory service is not configured")
        event.items.forEach {
            inventoryService.add(CommodityCode(it.skuId), BigDecimal(it.quantity)).onFailure {
                throw IllegalStateException("after-sale stock restore failed: ${it.errorCode}")
            }
        }
    }
}
