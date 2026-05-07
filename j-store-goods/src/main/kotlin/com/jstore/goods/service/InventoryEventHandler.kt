package com.jstore.goods.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.acl.event.StockReservationRequestedEvent
import com.jstore.goods.domain.inventory.CommodityCode
import com.jstore.goods.domain.inventory.event.StockReservationFailedEvent
import com.jstore.goods.domain.inventory.event.StockReservedEvent
import java.math.BigDecimal

/**
 * 库存应用层事件处理器：监听 ACL 集成事件，编排库存操作
 */
class InventoryReservationEventHandler(
    private val inventoryService: InventoryService,
    private val domainEventPublisher: DomainEventPublisher,
) : DomainEventListener<StockReservationRequestedEvent> {
    override fun listenerId(): String = "goods.inventory.reserve-stock-on-request"

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class)
    }

    override fun onDomainEvent(event: StockReservationRequestedEvent) {
        val orderId = event.orderId
        val bizCodePrefix = "ORDER-$orderId"

        log.info("收到库存预扣请求: orderId=$orderId")

        val reservedBizCodes = mutableListOf<String>()

        for (item in event.items) {
            val bizCode = "$bizCodePrefix-SKU-${item.skuId}"
            val result = inventoryService.reserve(
                bizCode = bizCode,
                commodityCode = CommodityCode(item.skuId),
                amount = BigDecimal(item.quantity),
            )

            when (result) {
                is Success -> reservedBizCodes.add(bizCode)
                is Failure -> {
                    log.warn("库存预扣失败: orderId=$orderId, skuId=${item.skuId}, error=${result.error.message}")
                    rollbackReservations(reservedBizCodes)
                    domainEventPublisher.publishEvent(
                        StockReservationFailedEvent(orderId = orderId, reason = "SKU ${item.skuId} 库存不足")
                    )
                    return
                }
            }
        }

        log.info("库存预扣全部成功: orderId=$orderId")
        domainEventPublisher.publishEvent(StockReservedEvent(orderId = orderId))
    }

    private fun rollbackReservations(bizCodes: List<String>) {
        for (bizCode in bizCodes) {
            try {
                inventoryService.release(bizCode)
            } catch (e: Exception) {
                log.error("库存回滚失败: bizCode=$bizCode, error=${e.message}")
            }
        }
    }
}
