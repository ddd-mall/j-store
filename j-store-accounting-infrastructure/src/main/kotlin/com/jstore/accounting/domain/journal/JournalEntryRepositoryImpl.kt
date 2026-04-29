package com.jstore.accounting.domain.journal

import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.accounting.domain.journal.persistence.JournalEntryPO
import com.jstore.accounting.domain.journal.persistence.JournalEntryPOJpaRepository
import com.jstore.accounting.domain.journal.persistence.JournalLinePO
import com.jstore.common.properties.Price
import org.springframework.stereotype.Repository
import java.util.concurrent.atomic.AtomicLong

@Repository
class JournalEntryRepositoryImpl(
    private val jpaRepository: JournalEntryPOJpaRepository,
) : JournalEntryRepository {
    override fun save(entity: JournalEntry): JournalEntry {
        val saved = jpaRepository.save(Converter.toPO(entity))
        return Converter.toDomain(saved)
    }

    override fun findById(id: JournalEntryId): JournalEntry? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry? =
        jpaRepository.findBySourceTypeAndSourceIdAndSourceEventType(
            sourceDocument.sourceType,
            sourceDocument.sourceId,
            sourceDocument.eventType,
        )?.let(Converter::toDomain)

    override fun nextId(): JournalEntryId = JournalEntryId(sequence.incrementAndGet())

    override fun nextLineId(): JournalLineId = JournalLineId(sequence.incrementAndGet())

    override fun nextEntryNo(type: JournalEntryType): String = "${type.name}-${System.currentTimeMillis()}-${sequence.incrementAndGet()}"

    override fun summarizeBalance(query: AccountingBalanceQuery): List<AccountingBalanceView> {
        val entries = jpaRepository.findAll()
            .filter { it.status == JournalEntryStatus.POSTED }
            .filter { query.startDate == null || !it.accountingDate.isBefore(query.startDate) }
            .filter { query.endDate == null || !it.accountingDate.isAfter(query.endDate) }
        val queryAccountId = query.accountId?.value
        val lines = entries.flatMap { it.lines }
            .filter { queryAccountId == null || it.accountId == queryAccountId }
        return lines.groupBy { it.accountId }.map { (accountId, accountLines) ->
            val debit = Price.ofFen(accountLines.filter { it.side == EntrySide.DEBIT }.sumOf { it.amountFen })
            val credit = Price.ofFen(accountLines.filter { it.side == EntrySide.CREDIT }.sumOf { it.amountFen })
            val balance = if (debit >= credit) debit - credit else credit - debit
            AccountingBalanceView(LedgerAccountId(accountId), debit, credit, balance)
        }
    }

    object Converter {
        fun toPO(entry: JournalEntry): JournalEntryPO =
            JournalEntryPO(
                id = entry.id.value,
                entryNo = entry.entryNo,
                entryType = entry.type,
                sourceType = entry.sourceDocument.sourceType,
                sourceId = entry.sourceDocument.sourceId,
                sourceEventType = entry.sourceDocument.eventType,
                accountingDate = entry.accountingDate,
                status = entry.status,
                reversedBy = entry.reversedBy?.value,
                createdAt = entry.createdAt,
                postedAt = entry.postedAt,
                lines = entry.lines.map { toLinePO(it) }.toMutableList(),
            )

        private fun toLinePO(line: JournalLine): JournalLinePO =
            JournalLinePO(
                id = line.id.value,
                accountId = line.accountId.value,
                side = line.side,
                amountFen = line.amount.fen,
                memo = line.memo,
            )

        fun toDomain(po: JournalEntryPO): JournalEntry =
            JournalEntryImpl(
                id = JournalEntryId(po.id),
                entryNo = po.entryNo,
                type = po.entryType,
                sourceDocument = SourceDocument(po.sourceType, po.sourceId, po.sourceEventType),
                accountingDate = po.accountingDate,
                _lines = po.lines.map { toLineDomain(it) }.toMutableList(),
                _status = po.status,
                createdAt = po.createdAt,
                _postedAt = po.postedAt,
                _reversedBy = po.reversedBy?.let(::JournalEntryId),
            )

        private fun toLineDomain(po: JournalLinePO): JournalLine =
            JournalLine(
                id = JournalLineId(po.id),
                accountId = LedgerAccountId(po.accountId),
                side = po.side,
                amount = Price.ofFen(po.amountFen),
                memo = po.memo,
            )
    }

    companion object {
        private val sequence = AtomicLong(System.currentTimeMillis())
    }
}
