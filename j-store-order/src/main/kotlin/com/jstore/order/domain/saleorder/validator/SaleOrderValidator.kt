package com.jstore.order.domain.saleorder.validator

import com.jstore.order.domain.saleorder.SaleOrder
import com.jstore.common.utils.ChainedConsumer
import org.springframework.stereotype.Component

abstract class AbstractSaleOrderValidator: ChainedConsumer<SaleOrder>()

@Component
class SaleOrderRiskValidator : AbstractSaleOrderValidator() {
    override fun accept(t: SaleOrder) {
        stockValid(t)
    }

    private fun stockValid(order: SaleOrder) {

    }
}