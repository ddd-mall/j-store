package com.jstore.order.domain.risk

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RiskFactory {
    private val log: Logger = LoggerFactory.getLogger(this::class)

    fun get(cmd: SaleOrderCreateRiskVerifyCmd): Risk {
        log.warn("[TODO] - sale order create risk verification not implemented yet")
        return RiskImpl()
    }
}