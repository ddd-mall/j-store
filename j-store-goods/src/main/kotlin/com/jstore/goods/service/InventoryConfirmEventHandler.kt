package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.onFailure
import com.jstore.goods.acl.event.StockConfirmRequestedEvent

/**
 * 库存应用层事件处理器：监听确认扣减请求，将预扣转为真正扣减
 */
class InventoryConfirmEventHandler(
    private val inventoryService: InventoryService,
) : DomainEventListener<StockConfirmRequestedEvent> {
    override fun listenerId(): String = "goods.inventory.confirm-stock-on-request"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun onDomainEvent(event: StockConfirmRequestedEvent) {
        val orderId = event.orderId
        log.info("收到库存确认扣减请求: orderId=$orderId")

        for (item in event.items) {
            val bizCode = "ORDER-$orderId-SKU-${item.skuId}"
            inventoryService.confirm(bizCode).onFailure {
                log.error("库存确认扣减失败: bizCode=$bizCode, error=${it.message}")
            }
        }

        log.info("库存确认扣减完成: orderId=$orderId")
    }
}
