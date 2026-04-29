package com.jstore.accounting.domain.settlement.event

import com.jstore.accounting.domain.settlement.SettlementPeriod
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price

data class SettlementConfirmedEvent(
    val settlementId: SettlementStatementId,
    val statementNo: String,
    val merchantId: String,
    val payableAmount: Price,
    val period: SettlementPeriod,
) : DomainEvent {
    override val source: Any get() = settlementId
}
