package com.jstore.order.saleorder

import org.springframework.stereotype.Service

@Service
class SaleOrderHandler(
    private val saleOrderRepository: SaleOrderRepository,
    private val saleOrderFactory: SaleOrderFactory,
    private val saleOrderEventPublisher: SaleOrderEventPublisher
) {
    fun create(cmd: SaleOrderCreateCMD): SaleOrder {
        val saleOrder = this.saleOrderFactory.create(cmd)
        val saved = saleOrderRepository.save(saleOrder)
        saleOrderEventPublisher.publish(SaleOrderCreatedEvent(saved.getId()!!, saved.createTime!!))
        return saved
    }
}