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
package com.jstore.accounting.domain.settlement

import com.jstore.accounting.domain.settlement.persistence.SettlementLinePO
import com.jstore.accounting.domain.settlement.persistence.SettlementStatementPO
import com.jstore.accounting.domain.settlement.persistence.SettlementStatementPOJpaRepository
import com.jstore.common.properties.Price
import java.util.concurrent.atomic.AtomicLong
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class SettlementStatementRepositoryImpl(
    private val jpaRepository: SettlementStatementPOJpaRepository
) : SettlementStatementRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: SettlementStatement): SettlementStatement {
        val saved = jpaRepository.save(Converter.toPO(entity))
        return Converter.toDomain(saved)
    }

    override fun findById(id: SettlementStatementId): SettlementStatement? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findByMerchantAndPeriod(
        merchantId: String,
        period: SettlementPeriod,
    ): SettlementStatement? =
        jpaRepository
            .findByMerchantIdAndPeriodStartAndPeriodEnd(
                merchantId,
                period.startDate,
                period.endDate,
            )
            ?.let(Converter::toDomain)

    override fun nextId(): SettlementStatementId = SettlementStatementId(sequence.incrementAndGet())

    override fun nextLineId(): SettlementLineId = SettlementLineId(sequence.incrementAndGet())

    override fun nextStatementNo(): String =
        "ST${System.currentTimeMillis()}${sequence.incrementAndGet()}"

    object Converter {
        fun toPO(statement: SettlementStatement): SettlementStatementPO =
            SettlementStatementPO(
                id = statement.id.value,
                statementNo = statement.statementNo,
                merchantId = statement.merchantId,
                periodStart = statement.period.startDate,
                periodEnd = statement.period.endDate,
                status = statement.status,
                payableAmountFen = statement.payableAmount.fen,
                confirmedAt = statement.confirmedAt,
                paidAt = statement.paidAt,
                lines = statement.lines.map { toLinePO(it) }.toMutableList(),
            )

        private fun toLinePO(line: SettlementLine): SettlementLinePO =
            SettlementLinePO(
                id = line.id.value,
                orderId = line.orderId,
                grossAmountFen = line.grossAmount.fen,
                refundAmountFen = line.refundAmount.fen,
                commissionAmountFen = line.commissionAmount.fen,
                netAmountFen = line.netAmount.fen,
            )

        fun toDomain(po: SettlementStatementPO): SettlementStatement =
            SettlementStatementImpl(
                id = SettlementStatementId(po.id),
                statementNo = po.statementNo,
                merchantId = po.merchantId,
                period = SettlementPeriod(po.periodStart, po.periodEnd),
                _lines = po.lines.map { toLineDomain(it) }.toMutableList(),
                _status = po.status,
                _payableAmount = Price.ofFen(po.payableAmountFen),
                _confirmedAt = po.confirmedAt,
                _paidAt = po.paidAt,
            )

        private fun toLineDomain(po: SettlementLinePO): SettlementLine =
            SettlementLine(
                id = SettlementLineId(po.id),
                orderId = po.orderId,
                grossAmount = Price.ofFen(po.grossAmountFen),
                refundAmount = Price.ofFen(po.refundAmountFen),
                commissionAmount = Price.ofFen(po.commissionAmountFen),
                netAmount = Price.ofFen(po.netAmountFen),
            )
    }

    companion object {
        private val sequence = AtomicLong(System.currentTimeMillis())
    }
}
