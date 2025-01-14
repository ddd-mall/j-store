package com.jstore.order.domain.saleorder.validator

import com.jstore.common.logging.LoggerFactory
import com.jstore.common.utils.ChainedConsumer
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCmd
import org.springframework.stereotype.Component

abstract class AbstractSaleOrderCreateCMDValidator : ChainedConsumer<NormalSaleOrderCreateCmd>()

@Component
class SaleOrderCreateCMDUserInfoValidator : AbstractSaleOrderCreateCMDValidator() {
    private val log = LoggerFactory.getLogger(this::class)

    override fun accept(t: NormalSaleOrderCreateCmd) {
        log.info("[user info validator] start validate user info")
        verifyToken(t)
    }


    private fun verifyToken(cmd: NormalSaleOrderCreateCmd) {

    }
}