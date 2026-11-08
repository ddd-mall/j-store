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
package com.jstore.accounting.domain.settlement.persistence

import com.jstore.accounting.domain.settlement.SettlementStatementStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "accounting_settlement_statement",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_accounting_settlement_merchant_period",
                columnNames = ["merchant_id", "period_start", "period_end"],
            )
        ],
)
class SettlementStatementPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "statement_no", nullable = false, length = 64) var statementNo: String = "",
    @Column(name = "merchant_id", nullable = false, length = 128) var merchantId: String = "",
    @Column(name = "period_start", nullable = false) var periodStart: LocalDate = LocalDate.now(),
    @Column(name = "period_end", nullable = false) var periodEnd: LocalDate = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: SettlementStatementStatus = SettlementStatementStatus.DRAFT,
    @Column(name = "payable_amount_fen", nullable = false) var payableAmountFen: Long = 0,
    @Column(name = "confirmed_at") var confirmedAt: Instant? = null,
    @Column(name = "paid_at") var paidAt: Instant? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "statement_id")
    var lines: MutableList<SettlementLinePO> = mutableListOf(),
)

@Entity
@Table(name = "accounting_settlement_line")
class SettlementLinePO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "order_id", nullable = false, length = 128) var orderId: String = "",
    @Column(name = "gross_amount_fen", nullable = false) var grossAmountFen: Long = 0,
    @Column(name = "refund_amount_fen", nullable = false) var refundAmountFen: Long = 0,
    @Column(name = "commission_amount_fen", nullable = false) var commissionAmountFen: Long = 0,
    @Column(name = "net_amount_fen", nullable = false) var netAmountFen: Long = 0,
)
