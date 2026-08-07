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

import com.jstore.accounting.AccountingJpaTestConfig
import com.jstore.accounting.domain.settlement.persistence.SettlementStatementPOJpaRepository
import com.jstore.common.properties.Price
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional

@SpringBootTest(classes = [AccountingJpaTestConfig::class])
@TestPropertySource(
    properties =
        [
            "spring.datasource.url=jdbc:h2:mem:accounting-settlement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.datasource.driverClassName=org.h2.Driver",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.flyway.enabled=false",
        ]
)
@Transactional
class SettlementStatementRepositoryImplTest
@Autowired
constructor(private val jpaRepository: SettlementStatementPOJpaRepository) {
    private lateinit var repository: SettlementStatementRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = SettlementStatementRepositoryImpl(jpaRepository)
    }

    @Test
    fun `settlement statement saves and loads with lines`() {
        val statement =
            SettlementStatementImpl(
                id = SettlementStatementId(1),
                statementNo = "ST1",
                merchantId = "m1",
                period = SettlementPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
            )
        statement.addLine(
            SettlementLine(
                SettlementLineId(11),
                "order-1",
                Price.ofFen(1000),
                Price.ZERO,
                Price.ofFen(100),
                Price.ofFen(900),
            )
        )
        statement.confirm()

        repository.save(statement)
        val restored =
            repository.findByMerchantAndPeriod(
                "m1",
                SettlementPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
            )!!

        restored.lines shouldHaveSize 1
        restored.payableAmount shouldBe Price.ofFen(900)
        restored.status shouldBe SettlementStatementStatus.CONFIRMED
    }
}
