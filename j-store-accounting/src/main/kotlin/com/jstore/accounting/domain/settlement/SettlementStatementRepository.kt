package com.jstore.accounting.domain.settlement

import com.jstore.common.framework.Repository

interface SettlementStatementRepository : Repository<SettlementStatementId, SettlementStatement> {
    fun findByMerchantAndPeriod(merchantId: String, period: SettlementPeriod): SettlementStatement?

    fun nextId(): SettlementStatementId

    fun nextLineId(): SettlementLineId

    fun nextStatementNo(): String
}
