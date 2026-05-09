package com.jstore.accounting.domain.settlement.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SettlementStatementPOJpaRepository : JpaRepository<SettlementStatementPO, Long> {
    fun findByMerchantIdAndPeriodStartAndPeriodEnd(
        merchantId: String,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): SettlementStatementPO?
}
