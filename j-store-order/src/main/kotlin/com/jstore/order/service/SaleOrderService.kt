package com.jstore.order.service

import com.jstore.order.domain.saleorder.SaleOrderCreateCMDHandler
import com.jstore.order.domain.saleorder.SaleOrderCreateCmd
import com.jstore.order.domain.saleorder.SaleOrder
import org.springframework.stereotype.Service

import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class SaleOrderService(
    private val saleOrderCreateCMDHandler: SaleOrderCreateCMDHandler,
) {

    @Transactional(
        rollbackFor = [Exception::class],
        propagation = Propagation.REQUIRES_NEW
    )
    fun create(cmd: SaleOrderCreateCmd): SaleOrder {
        val saleOrder = saleOrderCreateCMDHandler.create(cmd)
        return saleOrder
    }
}