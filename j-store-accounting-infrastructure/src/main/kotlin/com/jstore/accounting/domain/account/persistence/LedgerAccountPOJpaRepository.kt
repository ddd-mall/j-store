package com.jstore.accounting.domain.account.persistence

import com.jstore.accounting.domain.account.SubjectType
import org.springframework.data.jpa.repository.JpaRepository

interface LedgerAccountPOJpaRepository : JpaRepository<LedgerAccountPO, Long> {
    fun findByCodeAndSubjectTypeAndSubjectId(
        code: String,
        subjectType: SubjectType,
        subjectId: String,
    ): LedgerAccountPO?
}
