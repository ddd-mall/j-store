package com.jstore.order.domain.risk

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.order.domain.saleorder.properties.UserInfo
import org.springframework.stereotype.Component

class SaleOrderCreateRiskVerifyCmd(
    val token: String,
    val userInfo: UserInfo
)

@Component
class SaleOrderCreateRiskVerifyCmdHandler(
    private val riskFactory: RiskFactory
) {
    private val log: Logger = LoggerFactory.getLogger(this::class)

    fun verify(cmd: SaleOrderCreateRiskVerifyCmd) {
        val risk = riskFactory.get(cmd)
        risk.checkRisk()
        risk.handleRisk()
        log.warn("[TODO] - risk verification when sale order create not implemented yet")
    }
}
