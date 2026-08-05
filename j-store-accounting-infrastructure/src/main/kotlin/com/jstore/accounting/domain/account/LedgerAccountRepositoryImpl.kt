package com.jstore.accounting.domain.account

import com.jstore.accounting.domain.account.persistence.LedgerAccountPO
import com.jstore.accounting.domain.account.persistence.LedgerAccountPOJpaRepository
import java.time.Instant
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LedgerAccountRepositoryImpl(private val jpaRepository: LedgerAccountPOJpaRepository) :
    LedgerAccountRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: LedgerAccount): LedgerAccount {
        val saved = jpaRepository.save(Converter.toPO(entity))
        return Converter.toDomain(saved)
    }

    override fun findById(id: LedgerAccountId): LedgerAccount? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findByCodeAndSubject(
        code: LedgerAccountCode,
        subject: AccountingSubject,
    ): LedgerAccount? =
        jpaRepository
            .findByCodeAndSubjectTypeAndSubjectId(
                code.value,
                subject.subjectType,
                subject.subjectId,
            )
            ?.let(Converter::toDomain)

    object Converter {
        fun toPO(account: LedgerAccount): LedgerAccountPO =
            LedgerAccountPO(
                id = account.id.value,
                code = account.code.value,
                name = account.name,
                accountType = account.type,
                balanceDirection = account.direction,
                subjectType = account.subject.subjectType,
                subjectId = account.subject.subjectId,
                status = account.status,
                updatedAt = Instant.now(),
            )

        fun toDomain(po: LedgerAccountPO): LedgerAccount =
            LedgerAccountImpl(
                id = LedgerAccountId(po.id),
                code = LedgerAccountCode(po.code),
                name = po.name,
                type = po.accountType,
                direction = po.balanceDirection,
                subject = AccountingSubject(po.subjectType, po.subjectId),
                _status = po.status,
            )
    }
}
