package com.jstore.order.saleorder.validator

import com.jstore.order.saleorder.SaleOrderCreateCMD
import com.jstore.common.errors.CommonErrors

class CreateParamUserInfoValidator: AbstractSaleOrderCreateParamValidator() {
    override fun accept(t: SaleOrderCreateCMD) {
        t.buyerUserInfo?: throw CommonErrors.INVALID_PARAM.withMsg("用户信息不能为空")
    }
}