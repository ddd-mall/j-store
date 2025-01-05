package com.jstore.order.saleorder.validator

import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.saleorder.SaleOrderCreateCMD


class SaleOrderCreateCMDValidChain : ChainedConsumer.ConsumerChain<SaleOrderCreateCMD>() {

    fun appendAll(vararg validator: AbstractSaleOrderCreateCMDValidator): SaleOrderCreateCMDValidChain {
        validator.forEach(::append)
        return this
    }

}