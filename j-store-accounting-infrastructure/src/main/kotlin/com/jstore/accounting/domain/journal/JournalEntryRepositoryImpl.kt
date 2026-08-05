/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.accounting.domain.journal

import com.jstore.accounting.domain.account.LedgerAccountId
import com.jstore.accounting.domain.journal.persistence.JournalEntryPO
import com.jstore.accounting.domain.journal.persistence.JournalEntryPOJpaRepository
import com.jstore.accounting.domain.journal.persistence.JournalLinePO
import com.jstore.common.properties.Price
import java.util.concurrent.atomic.AtomicLong
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class JournalEntryRepositoryImpl(private val jpaRepository: JournalEntryPOJpaRepository) :
    JournalEntryRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: JournalEntry): JournalEntry {
        val saved = jpaRepository.save(Converter.toPO(entity))
        return Converter.toDomain(saved)
    }

    override fun findById(id: JournalEntryId): JournalEntry? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry? =
        jpaRepository
            .findBySourceTypeAndSourceIdAndSourceEventType(
                sourceDocument.sourceType,
                sourceDocument.sourceId,
                sourceDocument.eventType,
            )
            ?.let(Converter::toDomain)

    override fun nextId(): JournalEntryId = JournalEntryId(sequence.incrementAndGet())

    override fun nextLineId(): JournalLineId = JournalLineId(sequence.incrementAndGet())

    override fun nextEntryNo(type: JournalEntryType): String =
        "${type.name}-${System.currentTimeMillis()}-${sequence.incrementAndGet()}"

    override fun summarizeBalance(query: AccountingBalanceQuery): List<AccountingBalanceView> {
        val entries =
            jpaRepository
                .findAll()
                .filter { it.status == JournalEntryStatus.POSTED }
                .filter { query.startDate == null || !it.accountingDate.isBefore(query.startDate) }
                .filter { query.endDate == null || !it.accountingDate.isAfter(query.endDate) }
        val queryAccountId = query.accountId?.value
        val lines =
            entries
                .flatMap { it.lines }
                .filter { queryAccountId == null || it.accountId == queryAccountId }
        return lines
            .groupBy { it.accountId }
            .map { (accountId, accountLines) ->
                val debit =
                    Price.ofFen(
                        accountLines.filter { it.side == EntrySide.DEBIT }.sumOf { it.amountFen }
                    )
                val credit =
                    Price.ofFen(
                        accountLines.filter { it.side == EntrySide.CREDIT }.sumOf { it.amountFen }
                    )
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
                reversalOf = entry.reversalOf?.value,
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
                _reversalOf = po.reversalOf?.let(::JournalEntryId),
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
