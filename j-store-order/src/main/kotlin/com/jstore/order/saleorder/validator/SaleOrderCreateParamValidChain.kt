package com.jstore.order.saleorder.validator

import com.jstore.order.saleorder.service.SaleOrderCreateParam
import com.jstore.common.utils.ChainedConsumer
import org.springframework.stereotype.Component

@Component
class SaleOrderCreateParamValidChain(validatorList: List<AbstractSaleOrderCreateParamValidator>?)
    : ChainedConsumer.ConsumerChain<SaleOrderCreateParam>() {
        init {
            validatorList?.forEach(::append)
        }
}