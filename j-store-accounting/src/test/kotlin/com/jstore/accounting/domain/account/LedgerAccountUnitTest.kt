package com.jstore.accounting.domain.account

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LedgerAccountUnitTest : FunSpec({
    test("ledger account can transition between active and inactive") {
        val account = LedgerAccountImpl(
            id = LedgerAccountId(1),
            code = LedgerAccountCode("1010"),
            name = "支付渠道清算",
            type = LedgerAccountType.ASSET,
            direction = BalanceDirection.DEBIT,
            subject = AccountingSubject(SubjectType.CHANNEL, "DEFAULT"),
            _status = LedgerAccountStatus.ACTIVE,
        )

        account.deactivate().shouldBe(Success(Unit))
        account.status shouldBe LedgerAccountStatus.INACTIVE
        account.activate().shouldBe(Success(Unit))
        account.status shouldBe LedgerAccountStatus.ACTIVE
    }

    test("repository requireActive rejects inactive account") {
        val inactive = LedgerAccountImpl(
            id = LedgerAccountId(1),
            code = LedgerAccountCode("1010"),
            name = "支付渠道清算",
            type = LedgerAccountType.ASSET,
            direction = BalanceDirection.DEBIT,
            subject = AccountingSubject(SubjectType.CHANNEL, "DEFAULT"),
            _status = LedgerAccountStatus.INACTIVE,
        )
        val repo = object : LedgerAccountRepository {
            override fun save(entity: LedgerAccount): LedgerAccount = entity
            override fun findById(id: LedgerAccountId): LedgerAccount? = inactive.takeIf { it.id == id }
            override fun findByCodeAndSubject(code: LedgerAccountCode, subject: AccountingSubject): LedgerAccount? = inactive
        }

        repo.requireActive(LedgerAccountId(1)).shouldBe(Failure(AccountingAccountErrors.LEDGER_ACCOUNT_INACTIVE))
    }
})
