package com.jstore.common.framework.event.outbox.persistence

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxEntryRepository
import com.jstore.common.framework.event.outbox.OutboxEntryStatus
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant

@SpringBootTest(classes = [OutboxEntryRepositoryImplPostgresTest.TestConfig::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxEntryRepositoryImplPostgresTest {

    @Autowired
    private lateinit var repository: OutboxEntryRepository

    @Autowired
    private lateinit var jpaRepository: OutboxEntryPOJpaRepository

    @BeforeEach
    fun cleanDatabase() {
        jpaRepository.deleteAll()
    }

    @Test
    fun `claim locks rows and increments attempt count without duplicate sequential claims`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repository.save(entry("claim-1", createdAt = base))
        repository.save(entry("claim-2", createdAt = base.plusSeconds(1)))
        repository.save(entry("claim-3", createdAt = base.plusSeconds(2)))

        val firstClaim = repository.claimPendingAndRetryable(
            maxRetryCount = 5,
            batchSize = 2,
            lockedBy = "worker-a",
            lockedUntil = Instant.now().plusSeconds(60)
        )
        val secondClaim = repository.claimPendingAndRetryable(
            maxRetryCount = 5,
            batchSize = 2,
            lockedBy = "worker-b",
            lockedUntil = Instant.now().plusSeconds(60)
        )

        assertEquals(listOf("claim-1", "claim-2"), firstClaim.map { it.id })
        assertEquals(listOf("claim-3"), secondClaim.map { it.id })
        firstClaim.forEach {
            assertEquals(OutboxEntryStatus.IN_PROGRESS, it.status)
            assertEquals(1, it.retryCount)
            assertEquals("worker-a", it.lockedBy)
            assertNotNull(it.lockedUntil)
        }
    }

    @Test
    fun `failed entries respect next attempt and expired locks are recovered until max retry`() {
        val now = Instant.now()
        repository.save(
            entry(
                id = "future-failed",
                status = OutboxEntryStatus.FAILED,
                retryCount = 1,
                nextAttemptAt = now.plusSeconds(60)
            )
        )
        repository.save(
            entry(
                id = "expired-lock",
                status = OutboxEntryStatus.IN_PROGRESS,
                retryCount = 4,
                lockedBy = "dead-worker",
                lockedAt = now.minusSeconds(120),
                lockedUntil = now.minusSeconds(60)
            )
        )
        repository.save(
            entry(
                id = "expired-exhausted",
                status = OutboxEntryStatus.IN_PROGRESS,
                retryCount = 5,
                lockedBy = "dead-worker",
                lockedAt = now.minusSeconds(120),
                lockedUntil = now.minusSeconds(60)
            )
        )

        val claimed = repository.claimPendingAndRetryable(
            maxRetryCount = 5,
            batchSize = 10,
            lockedBy = "worker-a",
            lockedUntil = now.plusSeconds(60)
        )

        assertEquals(listOf("expired-lock"), claimed.map { it.id })
        assertEquals(5, claimed.single().retryCount)
        assertEquals(OutboxEntryStatus.IN_PROGRESS, claimed.single().status)

        val futureFailed = jpaRepository.findById("future-failed").orElseThrow()
        assertEquals(OutboxEntryStatus.FAILED, futureFailed.status)

        val exhausted = jpaRepository.findById("expired-exhausted").orElseThrow()
        assertEquals(OutboxEntryStatus.DEAD_LETTER, exhausted.status)
        assertEquals(null, exhausted.lockedBy)
        assertEquals("Outbox relay lock expired after max retry count", exhausted.lastError)
    }

    @Test
    fun `mark result requires current lock owner`() {
        repository.save(entry("owner-check"))
        val claimed = repository.claimPendingAndRetryable(
            maxRetryCount = 5,
            batchSize = 1,
            lockedBy = "worker-a",
            lockedUntil = Instant.now().plusSeconds(60)
        ).single()

        val wrongOwnerUpdated = repository.markPublished(
            claimed.copy(updatedAt = Instant.now()),
            lockedBy = "worker-b"
        )
        assertFalse(wrongOwnerUpdated)
        assertEquals(OutboxEntryStatus.IN_PROGRESS, jpaRepository.findById("owner-check").orElseThrow().status)

        val rightOwnerUpdated = repository.markPublished(
            claimed.copy(updatedAt = Instant.now()),
            lockedBy = "worker-a"
        )
        assertTrue(rightOwnerUpdated)
        assertEquals(OutboxEntryStatus.PUBLISHED, jpaRepository.findById("owner-check").orElseThrow().status)
    }

    @Test
    fun `delete published before honors batch size`() {
        val old = Instant.parse("2025-01-01T00:00:00Z")
        repository.save(entry("old-1", status = OutboxEntryStatus.PUBLISHED, createdAt = old))
        repository.save(entry("old-2", status = OutboxEntryStatus.PUBLISHED, createdAt = old.plusSeconds(1)))
        repository.save(entry("old-3", status = OutboxEntryStatus.PUBLISHED, createdAt = old.plusSeconds(2)))
        repository.save(entry("pending-old", status = OutboxEntryStatus.PENDING, createdAt = old))

        val deleted = repository.deletePublishedBefore(Instant.parse("2025-01-02T00:00:00Z"), 2)

        assertEquals(2, deleted)
        assertEquals(2, jpaRepository.count())
        assertTrue(jpaRepository.existsById("old-3"))
        assertTrue(jpaRepository.existsById("pending-old"))
    }

    @Test
    fun `dead letters can be queried counted and requeued`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repository.save(entry("dead-1", status = OutboxEntryStatus.DEAD_LETTER, createdAt = base, updatedAt = base.plusSeconds(2)))
        repository.save(entry("dead-2", status = OutboxEntryStatus.DEAD_LETTER, createdAt = base, updatedAt = base.plusSeconds(1)))
        repository.save(entry("failed-1", status = OutboxEntryStatus.FAILED, createdAt = base))

        val deadLetters = repository.findDeadLetters(batchSize = 10)

        assertEquals(listOf("dead-2", "dead-1"), deadLetters.map { it.id })
        assertEquals(2L, repository.countByStatus(OutboxEntryStatus.DEAD_LETTER))
        assertEquals(1L, repository.countByStatus(OutboxEntryStatus.FAILED))

        val nextAttemptAt = Instant.parse("2026-01-02T00:00:00Z")
        val requeued = repository.requeueDeadLetters(listOf("dead-1", "failed-1"), nextAttemptAt)

        assertEquals(1, requeued)
        val dead1 = jpaRepository.findById("dead-1").orElseThrow()
        assertEquals(OutboxEntryStatus.FAILED, dead1.status)
        assertEquals(nextAttemptAt, dead1.nextAttemptAt)
        assertEquals(null, dead1.lockedBy)
        assertEquals(null, dead1.lastError)
        assertEquals(1L, repository.countByStatus(OutboxEntryStatus.DEAD_LETTER))
        assertEquals(2L, repository.countByStatus(OutboxEntryStatus.FAILED))
    }

    private fun entry(
        id: String,
        status: OutboxEntryStatus = OutboxEntryStatus.PENDING,
        createdAt: Instant = Instant.now(),
        updatedAt: Instant = createdAt,
        retryCount: Int = 0,
        nextAttemptAt: Instant = createdAt,
        lockedBy: String? = null,
        lockedAt: Instant? = null,
        lockedUntil: Instant? = null,
        lastError: String? = null
    ) = OutboxEntry(
        id = id,
        eventType = "com.example.Event",
        payload = """{"source":"test"}""",
        aggregateType = "Order",
        aggregateId = id,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        retryCount = retryCount,
        nextAttemptAt = nextAttemptAt,
        lockedBy = lockedBy,
        lockedAt = lockedAt,
        lockedUntil = lockedUntil,
        lastError = lastError
    )

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [OutboxEntryPO::class])
    @EnableJpaRepositories(basePackageClasses = [OutboxEntryPOJpaRepository::class])
    class TestConfig {
        @Bean
        fun outboxEntryRepository(
            jpaRepository: OutboxEntryPOJpaRepository,
            entityManager: EntityManager,
        ): OutboxEntryRepository = OutboxEntryRepositoryImpl(jpaRepository, entityManager)
    }

    companion object {
        private val postgres: EmbeddedPostgres by lazy { EmbeddedPostgres.builder().start() }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.getJdbcUrl("postgres", "postgres") }
            registry.add("spring.datasource.username") { "postgres" }
            registry.add("spring.datasource.password") { "" }
            registry.add("spring.datasource.driver-class-name") { "org.postgresql.Driver" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.flyway.enabled") { "false" }
        }
    }
}
