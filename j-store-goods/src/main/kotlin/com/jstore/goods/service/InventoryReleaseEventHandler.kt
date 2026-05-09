package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.onFailure
import com.jstore.goods.acl.event.StockReleaseRequestedEvent

/**
 * 库存应用层事件处理器：监听释放请求，释放预扣库存
 */
class InventoryReleaseEventHandler(
    private val inventoryService: InventoryService,
) : DomainEventListener<StockReleaseRequestedEvent> {
    override fun listenerId(): String = "goods.inventory.release-stock-on-request"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun onDomainEvent(event: StockReleaseRequestedEvent) {
        val orderId = event.orderId
        log.info("收到库存释放请求: orderId=$orderId")

        for (item in event.items) {
            val bizCode = "ORDER-$orderId-SKU-${item.skuId}"
            inventoryService.release(bizCode).onFailure {
                log.warn("库存释放跳过或失败: bizCode=$bizCode, error=${it.message}")
            }
        }
    }
}
