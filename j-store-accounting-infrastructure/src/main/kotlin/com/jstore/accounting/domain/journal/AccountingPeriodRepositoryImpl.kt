package com.jstore.accounting.domain.journal

import com.jstore.accounting.domain.journal.persistence.AccountingPeriodPO
import com.jstore.accounting.domain.journal.persistence.AccountingPeriodPOJpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class AccountingPeriodRepositoryImpl(
    private val jpaRepository: AccountingPeriodPOJpaRepository,
) : AccountingPeriodRepository {
    override fun save(entity: AccountingPeriod): AccountingPeriod {
        val saved = jpaRepository.save(Converter.toPO(entity))
        return Converter.toDomain(saved)
    }

    override fun findById(id: AccountingPeriodId): AccountingPeriod? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findByDate(date: LocalDate): AccountingPeriod? =
        jpaRepository.findByDate(date)?.let(Converter::toDomain)

    object Converter {
        fun toPO(period: AccountingPeriod): AccountingPeriodPO =
            AccountingPeriodPO(
                id = period.id.value,
                periodCode = period.periodCode,
                startDate = period.startDate,
                endDate = period.endDate,
                status = period.status,
                closedAt = period.closedAt,
                closedBy = period.closedBy,
            )

        fun toDomain(po: AccountingPeriodPO): AccountingPeriod =
            AccountingPeriodImpl(
                id = AccountingPeriodId(po.id),
                periodCode = po.periodCode,
                startDate = po.startDate,
                endDate = po.endDate,
                _status = po.status,
                _closedAt = po.closedAt,
                _closedBy = po.closedBy,
            )
    }
}
