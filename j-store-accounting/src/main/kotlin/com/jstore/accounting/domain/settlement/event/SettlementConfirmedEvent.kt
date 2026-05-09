package com.jstore.accounting.domain.settlement.event

import com.jstore.accounting.domain.settlement.SettlementPeriod
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import com.jstore.common.framework.event.stableDomainEventId
import com.jstore.common.properties.Price
import java.time.Instant

@DomainEventType(name = "accounting.settlement-confirmed", version = 1)
data class SettlementConfirmedEvent(
    val settlementId: SettlementStatementId,
    val statementNo: String,
    val merchantId: String,
    val payableAmount: Price,
    val period: SettlementPeriod,
) : ExplicitDomainEvent {
    override val source: Any get() = settlementId
    override val eventName: String get() = "accounting.settlement-confirmed"
    override val eventVersion: Int get() = 1
    override val occurredAt: Instant = Instant.now()
    override val aggregateType: String get() = "SettlementStatement"
    override val aggregateId: String get() = settlementId.toString()
    override val eventId: String get() = stableDomainEventId(eventName, eventVersion, aggregateType, aggregateId, occurredAt)
}
