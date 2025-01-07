package com.jstore.order.risk

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.stereotype.Component

class VerifySaleOrderCreateRiskCmd

@Component
class VerifySaleOrderCreateRiskCmdHandler(private val riskFactory: RiskFactory) {
    private val log: Logger = LoggerFactory.getLogger(this::class)

    fun verify(cmd: VerifySaleOrderCreateRiskCmd) {
        val risk = riskFactory.create()
        risk.checkRisk()
        risk.handleRisk()
        log.error("sale order create risk verification not implemented yet")
    }
}
