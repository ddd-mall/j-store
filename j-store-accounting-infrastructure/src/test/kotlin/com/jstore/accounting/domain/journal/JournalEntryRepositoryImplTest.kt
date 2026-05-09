package com.jstore.accounting.domain.journal

import com.jstore.accounting.AccountingJpaTestConfig
import com.jstore.accounting.domain.account.BalanceDirection
import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.accounting.domain.account.LedgerAccountStatus
import com.jstore.accounting.domain.account.LedgerAccountType
import com.jstore.accounting.domain.account.SubjectType
import com.jstore.accounting.domain.account.persistence.LedgerAccountPO
import com.jstore.accounting.domain.account.persistence.LedgerAccountPOJpaRepository
import com.jstore.accounting.domain.journal.persistence.JournalEntryPOJpaRepository
import com.jstore.common.properties.Price
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertFailsWith

@SpringBootTest(classes = [AccountingJpaTestConfig::class])
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:accounting-journal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
    ]
)
@Transactional
class JournalEntryRepositoryImplTest @Autowired constructor(
    private val accountJpaRepository: LedgerAccountPOJpaRepository,
    private val journalEntryJpaRepository: JournalEntryPOJpaRepository,
) {
    private lateinit var repository: JournalEntryRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = JournalEntryRepositoryImpl(journalEntryJpaRepository)
        accountJpaRepository.save(account(1010, "1010", SubjectType.CHANNEL, "DEFAULT", LedgerAccountType.ASSET, BalanceDirection.DEBIT))
        accountJpaRepository.save(account(2101, "2101", SubjectType.MERCHANT, "DEFAULT", LedgerAccountType.LIABILITY, BalanceDirection.CREDIT))
    }

    @Test
    fun `journal entry saves and loads with lines`() {
        val entry = postedEntry(id = 1, sourceId = "order-1")

        repository.save(entry)
        val restored = repository.findById(JournalEntryId(1))!!

        restored.sourceDocument shouldBe SourceDocument(SourceDocumentType.ORDER, "order-1", "OrderPaidEvent")
        restored.lines shouldHaveSize 2
        restored.reversalOf shouldBe null
        restored.lines.first { it.side == EntrySide.DEBIT }.accountId shouldBe LedgerAccountId(1010)
        restored.lines.first { it.side == EntrySide.CREDIT }.accountId shouldBe LedgerAccountId(2101)
    }

    @Test
    fun `journal entry saves reversal origin reference`() {
        val reversal = JournalEntryImpl(
            id = JournalEntryId(3),
            entryNo = "JE3",
            type = JournalEntryType.ORDER_REFUND_REVERSAL,
            sourceDocument = SourceDocument(SourceDocumentType.REFUND, "refund-1", "OrderRefundApprovedEvent"),
            accountingDate = LocalDate.of(2026, 4, 30),
            _lines = mutableListOf(
                JournalLine(JournalLineId(31), LedgerAccountId(2101), EntrySide.DEBIT, Price.ofFen(500), "refund debit"),
                JournalLine(JournalLineId(32), LedgerAccountId(1010), EntrySide.CREDIT, Price.ofFen(500), "refund credit"),
            ),
            _status = JournalEntryStatus.POSTED,
            _postedAt = Instant.parse("2026-04-30T01:00:00Z"),
            _reversalOf = JournalEntryId(1),
        )

        repository.save(reversal)

        repository.findById(JournalEntryId(3))!!.reversalOf shouldBe JournalEntryId(1)
    }

    @Test
    fun `source document unique constraint prevents duplicate posting`() {
        repository.save(postedEntry(id = 1, sourceId = "order-1"))

        assertFailsWith<DataIntegrityViolationException> {
            repository.save(postedEntry(id = 2, sourceId = "order-1"))
            journalEntryJpaRepository.flush()
        }
    }

    @Test
    fun `balance query only summarizes posted entries`() {
        repository.save(postedEntry(id = 1, sourceId = "order-1"))
        repository.save(draftEntry(id = 2, sourceId = "order-2"))

        val balances = repository.summarizeBalance(AccountingBalanceQuery(accountId = LedgerAccountId(1010)))

        balances shouldHaveSize 1
        balances.single().debitAmount shouldBe Price.ofFen(1000)
        balances.single().creditAmount shouldBe Price.ZERO
        balances.single().balance shouldBe Price.ofFen(1000)
    }

    private fun postedEntry(id: Long, sourceId: String): JournalEntry =
        JournalEntryImpl(
            id = JournalEntryId(id),
            entryNo = "JE$id",
            type = JournalEntryType.ORDER_PAYMENT,
            sourceDocument = SourceDocument(SourceDocumentType.ORDER, sourceId, "OrderPaidEvent"),
            accountingDate = LocalDate.of(2026, 4, 30),
            _lines = mutableListOf(
                JournalLine(JournalLineId(id * 10 + 1), LedgerAccountId(1010), EntrySide.DEBIT, Price.ofFen(1000), "debit"),
                JournalLine(JournalLineId(id * 10 + 2), LedgerAccountId(2101), EntrySide.CREDIT, Price.ofFen(1000), "credit"),
            ),
            _status = JournalEntryStatus.POSTED,
            _postedAt = Instant.parse("2026-04-30T01:00:00Z"),
        )

    private fun draftEntry(id: Long, sourceId: String): JournalEntry =
        JournalEntryImpl(
            id = JournalEntryId(id),
            entryNo = "JE$id",
            type = JournalEntryType.ORDER_PAYMENT,
            sourceDocument = SourceDocument(SourceDocumentType.ORDER, sourceId, "OrderPaidEvent"),
            accountingDate = LocalDate.of(2026, 4, 30),
            _lines = mutableListOf(
                JournalLine(JournalLineId(id * 10 + 1), LedgerAccountId(1010), EntrySide.DEBIT, Price.ofFen(500), "debit"),
                JournalLine(JournalLineId(id * 10 + 2), LedgerAccountId(2101), EntrySide.CREDIT, Price.ofFen(500), "credit"),
            ),
            _status = JournalEntryStatus.DRAFT,
        )

    private fun account(
        id: Long,
        code: String,
        subjectType: SubjectType,
        subjectId: String,
        type: LedgerAccountType,
        direction: BalanceDirection,
    ): LedgerAccountPO =
        LedgerAccountPO(
            id = id,
            code = code,
            name = code,
            accountType = type,
            balanceDirection = direction,
            subjectType = subjectType,
            subjectId = subjectId,
            status = LedgerAccountStatus.ACTIVE,
        )
}
