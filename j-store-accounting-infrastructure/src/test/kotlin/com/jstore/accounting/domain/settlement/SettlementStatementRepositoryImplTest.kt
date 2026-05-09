package com.jstore.accounting.domain.settlement

import com.jstore.accounting.AccountingJpaTestConfig
import com.jstore.accounting.domain.settlement.persistence.SettlementStatementPOJpaRepository
import com.jstore.common.properties.Price
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@SpringBootTest(classes = [AccountingJpaTestConfig::class])
@TestPropertySource(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:accounting-settlement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
    ]
)
@Transactional
class SettlementStatementRepositoryImplTest @Autowired constructor(
    private val jpaRepository: SettlementStatementPOJpaRepository,
) {
    private lateinit var repository: SettlementStatementRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = SettlementStatementRepositoryImpl(jpaRepository)
    }

    @Test
    fun `settlement statement saves and loads with lines`() {
        val statement = SettlementStatementImpl(
            id = SettlementStatementId(1),
            statementNo = "ST1",
            merchantId = "m1",
            period = SettlementPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)),
        )
        statement.addLine(SettlementLine(SettlementLineId(11), "order-1", Price.ofFen(1000), Price.ZERO, Price.ofFen(100), Price.ofFen(900)))
        statement.confirm()

        repository.save(statement)
        val restored = repository.findByMerchantAndPeriod("m1", SettlementPeriod(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))!!

        restored.lines shouldHaveSize 1
        restored.payableAmount shouldBe Price.ofFen(900)
        restored.status shouldBe SettlementStatementStatus.CONFIRMED
    }
}
