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
package com.jstore.accounting.config

import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.accounting.service.AccountingUseCase
import com.jstore.accounting.service.SettlementUseCase
import com.jstore.accounting.service.command.RecordOrderCompletedCMD
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.accounting.service.command.RecordOrderRefundApprovedCMD
import com.jstore.accounting.service.command.RecordSettlementPaidCMD
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalAccountingUseCase(
    private val delegate: AccountingUseCase,
    transactionManager: PlatformTransactionManager,
) : AccountingUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun findBySourceDocument(sourceDocument: SourceDocument) = query {
        delegate.findBySourceDocument(sourceDocument)
    }

    override fun recordOrderPaid(cmd: RecordOrderPaidCMD) = tx { delegate.recordOrderPaid(cmd) }

    override fun recordOrderCompleted(cmd: RecordOrderCompletedCMD) = tx {
        delegate.recordOrderCompleted(cmd)
    }

    override fun recordOrderRefundApproved(cmd: RecordOrderRefundApprovedCMD) = tx {
        delegate.recordOrderRefundApproved(cmd)
    }

    override fun recordSettlementPaid(cmd: RecordSettlementPaidCMD) = tx {
        delegate.recordSettlementPaid(cmd)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T? = read.execute { block() }
}

class TransactionalSettlementUseCase(
    private val delegate: SettlementUseCase,
    transactionManager: PlatformTransactionManager,
) : SettlementUseCase {
    private val write = TransactionTemplate(transactionManager)

    override fun confirmStatement(statementId: SettlementStatementId) = tx {
        delegate.confirmStatement(statementId)
    }

    override fun markPaid(statementId: SettlementStatementId, paidAt: Instant) = tx {
        delegate.markPaid(statementId, paidAt)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })
}
