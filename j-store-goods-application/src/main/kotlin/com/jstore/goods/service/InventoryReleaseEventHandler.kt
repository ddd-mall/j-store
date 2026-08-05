package com.jstore.goods.service

import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.onFailure
import com.jstore.contracts.commerce.ReleaseInventoryCommand

/** 库存应用层事件处理器：监听释放请求，释放预扣库存 */
class InventoryReleaseEventHandler(private val inventoryService: InventoryUseCase) :
    IntegrationMessageHandler<ReleaseInventoryCommand> {
    override fun handlerId(): String = "goods.inventory.release-stock-on-request.v2"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun handle(message: ReleaseInventoryCommand) {
        val event = message
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
