package com.jstore.order.saleorder.validator

import com.jstore.common.errors.CommonErrors
import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.saleorder.SaleOrderCreateCMD
import org.springframework.stereotype.Component

abstract class AbstractSaleOrderCreateCMDValidator : ChainedConsumer<SaleOrderCreateCMD>()

@Component
class SaleOrderCreateCMDUserInfoValidator : AbstractSaleOrderCreateCMDValidator() {

    override fun accept(t: SaleOrderCreateCMD) {
        t.buyerUserInfo ?: throw CommonErrors.INVALID_PARAM.withMsg("用户信息不能为空")
    }
}