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
import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import java.time.Instant
import java.time.LocalDate

data class JournalEntryId(override val value: Long) : Id<Long>(value)

data class JournalLineId(override val value: Long) : Id<Long>(value)

data class SourceDocument(
    val sourceType: SourceDocumentType,
    val sourceId: String,
    val eventType: String,
) {
    init {
        require(sourceId.isNotBlank()) { "来源单据ID不能为空" }
        require(eventType.isNotBlank()) { "来源事件类型不能为空" }
    }
}

enum class SourceDocumentType {
    ORDER,
    REFUND,
    SETTLEMENT,
    ADJUSTMENT,
}

enum class JournalEntryType {
    ORDER_PAYMENT,
    ORDER_COMPLETION_COMMISSION,
    ORDER_REFUND_REVERSAL,
    SETTLEMENT_PAYMENT,
    MANUAL_ADJUSTMENT,
}

enum class JournalEntryStatus {
    DRAFT,
    POSTED,
    REVERSED,
}

enum class EntrySide {
    DEBIT,
    CREDIT,
}

interface JournalEntry : AgreeGate<JournalEntryId> {
    override val id: JournalEntryId
    val entryNo: String
    val type: JournalEntryType
    val sourceDocument: SourceDocument
    val accountingDate: LocalDate
    val status: JournalEntryStatus
    val lines: List<JournalLine>
    val createdAt: Instant
    val postedAt: Instant?
    val reversedBy: JournalEntryId?
    val reversalOf: JournalEntryId?

    fun addLine(line: JournalLine): Result<Unit, BusinessError>

    fun post(openPeriod: AccountingPeriod): Result<Unit, BusinessError>

    fun markReversed(reversalEntryId: JournalEntryId): Result<Unit, BusinessError>

    fun createReversal(
        reversalEntryId: JournalEntryId,
        reversalEntryNo: String,
        accountingDate: LocalDate,
        reason: String,
    ): Result<JournalEntry, BusinessError>
}

data class JournalLine(
    val id: JournalLineId,
    val accountId: LedgerAccountId,
    val side: EntrySide,
    val amount: Price,
    val memo: String,
) {
    init {
        require(amount > Price.ZERO) { "账务分录金额必须大于0" }
        require(memo.isNotBlank()) { "账务分录摘要不能为空" }
    }
}
