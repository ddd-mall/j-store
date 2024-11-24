package com.jstore.com.jstore.order.saleorder.validator

import com.jstore.com.jstore.order.saleorder.service.SaleOrderCreateParam
import com.jstore.util.ChainedConsumer

class SaleOrderCreateParamValidChain(validatorList: List<AbstractSaleOrderCreateParamValidator>?)
    : ChainedConsumer.ConsumerChain<SaleOrderCreateParam>() {
        init {
            validatorList?.forEach(::append)
        }
}