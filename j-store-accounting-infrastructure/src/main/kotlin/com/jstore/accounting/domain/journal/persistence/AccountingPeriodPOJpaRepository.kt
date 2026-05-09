package com.jstore.accounting.domain.journal.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface AccountingPeriodPOJpaRepository : JpaRepository<AccountingPeriodPO, Long> {
    @Query("select p from AccountingPeriodPO p where p.startDate <= :date and p.endDate >= :date")
    fun findByDate(@Param("date") date: LocalDate): AccountingPeriodPO?
}
