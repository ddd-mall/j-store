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
package com.jstore.accounting.domain.account

import com.jstore.accounting.domain.account.persistence.LedgerAccountPO
import com.jstore.accounting.domain.account.persistence.LedgerAccountPOJpaRepository
import java.time.Instant
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class LedgerAccountRepositoryImpl(private val jpaRepository: LedgerAccountPOJpaRepository) :
    LedgerAccountRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: LedgerAccount): LedgerAccount {
        val saved = jpaRepository.save(Converter.toPO(entity))
        return Converter.toDomain(saved)
    }

    override fun findById(id: LedgerAccountId): LedgerAccount? =
        jpaRepository.findById(id.value).orElse(null)?.let(Converter::toDomain)

    override fun findByCodeAndSubject(
        code: LedgerAccountCode,
        subject: AccountingSubject,
    ): LedgerAccount? =
        jpaRepository
            .findByCodeAndSubjectTypeAndSubjectId(
                code.value,
                subject.subjectType,
                subject.subjectId,
            )
            ?.let(Converter::toDomain)

    object Converter {
        fun toPO(account: LedgerAccount): LedgerAccountPO =
            LedgerAccountPO(
                id = account.id.value,
                code = account.code.value,
                name = account.name,
                accountType = account.type,
                balanceDirection = account.direction,
                subjectType = account.subject.subjectType,
                subjectId = account.subject.subjectId,
                status = account.status,
                updatedAt = Instant.now(),
            )

        fun toDomain(po: LedgerAccountPO): LedgerAccount =
            LedgerAccountImpl(
                id = LedgerAccountId(po.id),
                code = LedgerAccountCode(po.code),
                name = po.name,
                type = po.accountType,
                direction = po.balanceDirection,
                subject = AccountingSubject(po.subjectType, po.subjectId),
                _status = po.status,
            )
    }
}
