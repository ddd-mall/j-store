package com.jstore.order.saleorder.validator

import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.saleorder.SaleOrder

class SaleOrderValidChain : ChainedConsumer.ConsumerChain<SaleOrder>() {

    fun appendAll(vararg validatorList: AbstractSaleOrderValidator): SaleOrderValidChain {
        validatorList.forEach(::append)
        return this
    }

}