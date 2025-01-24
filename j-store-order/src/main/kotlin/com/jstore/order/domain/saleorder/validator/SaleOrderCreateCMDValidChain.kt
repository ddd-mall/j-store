package com.jstore.order.domain.saleorder.validator

import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.domain.saleorder.SaleOrderCreateCmd


class SaleOrderCreateCMDValidChain : ChainedConsumer.ConsumerChain<SaleOrderCreateCmd>() {

    fun appendAll(vararg validator: AbstractSaleOrderCreateCMDValidator): SaleOrderCreateCMDValidChain {
        validator.forEach(::append)
        return this
    }

}