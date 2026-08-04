package com.jstore.accounting.domain.settlement.persistence

import java.time.LocalDate
import org.springframework.data.jpa.repository.JpaRepository

interface SettlementStatementPOJpaRepository : JpaRepository<SettlementStatementPO, Long> {
    fun findByMerchantIdAndPeriodStartAndPeriodEnd(
        merchantId: String,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): SettlementStatementPO?
}
