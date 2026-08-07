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

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import java.time.Instant
import java.time.LocalDate

data class SettlementStatementId(override val value: Long) : Id<Long>(value)

data class SettlementLineId(override val value: Long) : Id<Long>(value)

data class SettlementPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(!startDate.isAfter(endDate)) { "结算周期开始日期不能晚于结束日期" }
    }
}

enum class SettlementStatementStatus {
    DRAFT,
    CONFIRMED,
    PAID,
    CANCELLED,
}

data class SettlementLine(
    val id: SettlementLineId,
    val orderId: String,
    val grossAmount: Price,
    val refundAmount: Price,
    val commissionAmount: Price,
    val netAmount: Price,
) {
    init {
        require(orderId.isNotBlank()) { "结算订单ID不能为空" }
    }
}

interface SettlementStatement : AgreeGate<SettlementStatementId> {
    override val id: SettlementStatementId
    val statementNo: String
    val merchantId: String
    val period: SettlementPeriod
    val status: SettlementStatementStatus
    val lines: List<SettlementLine>
    val payableAmount: Price
    val confirmedAt: Instant?
    val paidAt: Instant?

    fun addLine(line: SettlementLine): Result<Unit, BusinessError>

    fun confirm(): Result<Unit, BusinessError>

    fun markPaid(paidAt: Instant): Result<Unit, BusinessError>
}
