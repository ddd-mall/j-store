package com.jstore.com.jstore.order.saleorder.validator

import com.jstore.order.saleorder.SaleOrder
import com.jstore.util.ChainedConsumer

class SaleOrderValidChain(validatorList: List<AbstractSaleOrderValidator>?): ChainedConsumer.ConsumerChain<SaleOrder>() {
    init {
        validatorList?.forEach(::append)
    }

    fun appendAll(validatorList: List<AbstractSaleOrderValidator>?) {
        validatorList?.forEach(::append)
    }

}