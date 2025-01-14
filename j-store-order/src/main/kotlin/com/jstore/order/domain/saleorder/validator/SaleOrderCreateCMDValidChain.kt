package com.jstore.order.domain.saleorder.validator

import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCmd


class SaleOrderCreateCMDValidChain : ChainedConsumer.ConsumerChain<NormalSaleOrderCreateCmd>() {

    fun appendAll(vararg validator: AbstractSaleOrderCreateCMDValidator): SaleOrderCreateCMDValidChain {
        validator.forEach(::append)
        return this
    }

}