package com.jstore.order.domain.saleorder.validator

import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.domain.saleorder.SaleOrder

class SaleOrderValidChain : ChainedConsumer.ConsumerChain<SaleOrder>() {

    fun appendAll(vararg validatorList: AbstractSaleOrderValidator): SaleOrderValidChain {
        validatorList.forEach(::append)
        return this
    }

}