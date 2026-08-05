package com.jstore.accounting.domain.settlement.event

import com.jstore.accounting.domain.settlement.SettlementPeriod
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.common.properties.Price
import java.time.Instant

@DomainEventType(name = "accounting.settlement-confirmed", version = 1)
data class SettlementConfirmedEvent(
    val settlementId: SettlementStatementId,
    val statementNo: String,
    val merchantId: String,
    val payableAmount: Price,
    val period: SettlementPeriod,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {

    override val eventName: String
        get() = "accounting.settlement-confirmed"

    override val eventVersion: Int
        get() = 1

    override val aggregateType: String
        get() = "SettlementStatement"

    override val aggregateId: String
        get() = settlementId.toString()
}
