package com.jstore.order.saleorder.validator

import com.jstore.order.saleorder.service.SaleOrderCreateCMD
import com.jstore.common.utils.ChainedConsumer
import org.springframework.stereotype.Component

@Component
class SaleOrderCreateParamValidChain(validatorList: List<AbstractSaleOrderCreateParamValidator>?)
    : ChainedConsumer.ConsumerChain<SaleOrderCreateCMD>() {
        init {
            validatorList?.forEach(::append)
        }
}