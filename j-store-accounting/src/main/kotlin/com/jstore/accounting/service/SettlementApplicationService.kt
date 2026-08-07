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

import com.jstore.accounting.domain.settlement.SettlementErrors
import com.jstore.accounting.domain.settlement.SettlementStatement
import com.jstore.accounting.domain.settlement.SettlementStatementId
import com.jstore.accounting.domain.settlement.SettlementStatementRepository
import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import java.time.Instant

class SettlementApplicationService(
    private val settlementStatementRepository: SettlementStatementRepository,
    private val domainEventPublisher: DomainEventPublisher? = null,
) {
    fun confirmStatement(
        statementId: SettlementStatementId
    ): Result<SettlementStatement, BusinessError> {
        val statement =
            settlementStatementRepository.findById(statementId)
                ?: return Failure(SettlementErrors.SETTLEMENT_STATEMENT_NOT_FOUND)
        statement.confirm().onFailure {
            return Failure(it)
        }
        return Success(settlementStatementRepository.save(statement))
    }

    fun markPaid(
        statementId: SettlementStatementId,
        paidAt: Instant,
    ): Result<SettlementStatement, BusinessError> {
        val statement =
            settlementStatementRepository.findById(statementId)
                ?: return Failure(SettlementErrors.SETTLEMENT_STATEMENT_NOT_FOUND)
        statement.markPaid(paidAt).onFailure {
            return Failure(it)
        }
        val saved = settlementStatementRepository.save(statement)
        saved.getDomainEvent().forEach { domainEventPublisher?.publishEvent(it) }
        return Success(saved)
    }
}
