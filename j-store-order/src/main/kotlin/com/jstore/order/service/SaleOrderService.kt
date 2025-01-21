package com.jstore.order.service

import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCMDHandler
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCmd
import com.jstore.order.domain.saleorder.SaleOrder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
open class SaleOrderService(
    private val normalSaleOrderCreateCMDHandler: NormalSaleOrderCreateCMDHandler,
) {

    @Transactional(
        rollbackFor = [Exception::class],
        timeout = 5,
        isolation = Isolation.READ_COMMITTED,
        propagation = Propagation.REQUIRED
    )
    open fun create(cmd: NormalSaleOrderCreateCmd): SaleOrder {

        val saleOrder = normalSaleOrderCreateCMDHandler.create(cmd)
        return saleOrder
    }
}