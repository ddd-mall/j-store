package com.jstore.order.service

import com.jstore.order.domain.saleorder.SaleOrder
import com.jstore.order.domain.saleorder.SaleOrderCreateCmd
import com.jstore.order.domain.saleorder.SaleOrderFactory
import com.jstore.order.domain.saleorder.SaleOrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SaleOrderService(
    private val inventoryService: InventoryService,
    private val saleOrderRepository: SaleOrderRepository,
    private val saleOrderFactory: SaleOrderFactory,
) {

    @Transactional(
        rollbackFor = [Exception::class],
        propagation = Propagation.REQUIRED
    )
    fun create(cmd: SaleOrderCreateCmd): SaleOrder {
        val saleOrder = this.saleOrderFactory.create(cmd)

        try {
            inventoryService.createAndReserve(saleOrder)
            saleOrder.initial()
        } catch (e: Exception) {
            saleOrder.cancel()
        }
        return saleOrderRepository.save(saleOrder)
    }
}