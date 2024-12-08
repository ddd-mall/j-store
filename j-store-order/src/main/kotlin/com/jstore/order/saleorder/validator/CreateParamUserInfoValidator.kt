package com.jstore.com.jstore.order.saleorder.validator

import com.jstore.common.errors.CommonErrors
import com.jstore.com.jstore.order.saleorder.service.SaleOrderCreateParam

class CreateParamUserInfoValidator: AbstractSaleOrderCreateParamValidator() {
    override fun accept(t: SaleOrderCreateParam) {
        t.buyerUserInfo?: throw CommonErrors.INVALID_PARAM.withMsg("用户信息不能为空")
    }
}