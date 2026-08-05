package com.jstore.goods.service

import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.RestoreInventoryAfterRefundCommand
import com.jstore.goods.domain.inventory.CommodityCode
import java.math.BigDecimal

class AfterSaleStockRestoreEventHandler(
    private val inventoryServiceProvider: () -> InventoryService?
) : IntegrationMessageHandler<RestoreInventoryAfterRefundCommand> {
    constructor(inventoryService: InventoryService) : this({ inventoryService })

    override fun handlerId() = "goods.after-sale-stock-restore.v2"

    override fun handle(message: RestoreInventoryAfterRefundCommand) {
        val event = message
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
