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
package com.jstore.accounting.service

import com.jstore.accounting.domain.journal.JournalEntry
import com.jstore.accounting.domain.journal.SourceDocument
import com.jstore.accounting.service.command.RecordOrderCompletedCMD
import com.jstore.accounting.service.command.RecordOrderPaidCMD
import com.jstore.accounting.service.command.RecordOrderRefundApprovedCMD
import com.jstore.accounting.service.command.RecordSettlementPaidCMD
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

interface AccountingUseCase {
    fun findBySourceDocument(sourceDocument: SourceDocument): JournalEntry?

    fun recordOrderPaid(cmd: RecordOrderPaidCMD): Result<JournalEntry, BusinessError>

    fun recordOrderCompleted(cmd: RecordOrderCompletedCMD): Result<JournalEntry, BusinessError>

    fun recordOrderRefundApproved(
        cmd: RecordOrderRefundApprovedCMD
    ): Result<JournalEntry, BusinessError>

    fun recordSettlementPaid(cmd: RecordSettlementPaidCMD): Result<JournalEntry, BusinessError>
}
