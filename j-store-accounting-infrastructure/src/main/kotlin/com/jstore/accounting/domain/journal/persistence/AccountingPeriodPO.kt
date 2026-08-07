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
package com.jstore.accounting.domain.journal.persistence

import com.jstore.accounting.domain.journal.PeriodStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "accounting_period")
class AccountingPeriodPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "period_code", nullable = false, length = 16) var periodCode: String = "",
    @Column(name = "start_date", nullable = false) var startDate: LocalDate = LocalDate.now(),
    @Column(name = "end_date", nullable = false) var endDate: LocalDate = LocalDate.now(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: PeriodStatus = PeriodStatus.OPEN,
    @Column(name = "closed_at") var closedAt: Instant? = null,
    @Column(name = "closed_by", length = 128) var closedBy: String? = null,
)
