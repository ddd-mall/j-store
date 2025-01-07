package com.jstore.order.risk

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RiskFactory {
    private val log: Logger = LoggerFactory.getLogger(this::class)

    fun create(): Risk {
        log.error("sale order create risk verification not implemented yet")
        return RiskImpl()
    }
}