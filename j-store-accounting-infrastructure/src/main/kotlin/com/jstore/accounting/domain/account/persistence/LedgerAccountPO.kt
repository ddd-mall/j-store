package com.jstore.accounting.domain.account.persistence

import com.jstore.accounting.domain.account.BalanceDirection
import com.jstore.accounting.domain.account.LedgerAccountStatus
import com.jstore.accounting.domain.account.LedgerAccountType
import com.jstore.accounting.domain.account.SubjectType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "accounting_ledger_account",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_accounting_ledger_account_code_subject",
                columnNames = ["code", "subject_type", "subject_id"],
            )
        ],
)
class LedgerAccountPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "code", nullable = false, length = 64) var code: String = "",
    @Column(name = "name", nullable = false, length = 128) var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32)
    var accountType: LedgerAccountType = LedgerAccountType.ASSET,
    @Enumerated(EnumType.STRING)
    @Column(name = "balance_direction", nullable = false, length = 16)
    var balanceDirection: BalanceDirection = BalanceDirection.DEBIT,
    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false, length = 32)
    var subjectType: SubjectType = SubjectType.PLATFORM,
    @Column(name = "subject_id", nullable = false, length = 128) var subjectId: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: LedgerAccountStatus = LedgerAccountStatus.ACTIVE,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now(),
)
