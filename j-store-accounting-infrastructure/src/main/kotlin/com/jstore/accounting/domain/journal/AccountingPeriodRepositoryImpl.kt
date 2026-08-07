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

import com.jstore.accounting.domain.journal.persistence.AccountingPeriodPO
import com.jstore.accounting.domain.journal.persistence.AccountingPeriodPOJpaRepository
import java.time.LocalDate
import org.springframework.stereotype.Repository

@Repository
class AccountingPeriodRepositoryImpl(private val jpaRepository: AccountingPeriodPOJpaRepository) :
    AccountingPeriodRepository {
    override fun save(entity: AccountingPeriod): AccountingPeriod {
        val saved = jpaRepository.save(Converter.toPO(entity))
        return Converter.toDomain(saved)
    }

    override fun findById(id: AccountingPeriodId): AccountingPeriod? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findByDate(date: LocalDate): AccountingPeriod? =
        jpaRepository.findByDate(date)?.let(Converter::toDomain)

    object Converter {
        fun toPO(period: AccountingPeriod): AccountingPeriodPO =
            AccountingPeriodPO(
                id = period.id.value,
                periodCode = period.periodCode,
                startDate = period.startDate,
                endDate = period.endDate,
                status = period.status,
                closedAt = period.closedAt,
                closedBy = period.closedBy,
            )

        fun toDomain(po: AccountingPeriodPO): AccountingPeriod =
            AccountingPeriodImpl(
                id = AccountingPeriodId(po.id),
                periodCode = po.periodCode,
                startDate = po.startDate,
                endDate = po.endDate,
                _status = po.status,
                _closedAt = po.closedAt,
                _closedBy = po.closedBy,
            )
    }
}
