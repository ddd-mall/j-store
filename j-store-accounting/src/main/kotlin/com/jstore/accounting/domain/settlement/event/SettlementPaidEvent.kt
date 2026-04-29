package com.jstore.accounting.domain.settlement.event

import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Price
import java.time.Instant

data class SettlementPaidEvent(
    val settlementId: SettlementStatementId,
    val statementNo: String,
    val merchantId: String,
    val payableAmount: Price,
    val paidAt: Instant,
) : DomainEvent {
    override val source: Any get() = settlementId
}
