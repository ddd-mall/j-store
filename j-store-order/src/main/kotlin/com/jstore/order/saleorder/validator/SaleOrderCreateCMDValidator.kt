package com.jstore.order.saleorder.validator

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.saleorder.NormalSaleOrderCreateCmd
import org.springframework.stereotype.Component

abstract class AbstractSaleOrderCreateCMDValidator : ChainedConsumer<NormalSaleOrderCreateCmd>()

@Component
class SaleOrderCreateCMDUserInfoValidator : AbstractSaleOrderCreateCMDValidator() {
    private val log = LoggerFactory.getLogger(this::class)

    override fun accept(t: NormalSaleOrderCreateCmd) {
        log.info("[user info validator] start validate user info")
        verifyToken(t)
        verifyUserInfo(t)
    }

    private fun verifyUserInfo(cmd: NormalSaleOrderCreateCmd) {
        cmd.buyerUserInfo ?: throw CommonErrors.INVALID_PARAM.to("用户信息不能为空")
    }

    private fun verifyToken(cmd: NormalSaleOrderCreateCmd) {

    }
}