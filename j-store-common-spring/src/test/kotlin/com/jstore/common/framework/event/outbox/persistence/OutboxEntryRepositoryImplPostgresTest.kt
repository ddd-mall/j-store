package com.jstore.common.framework.event.outbox.persistence

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.LocalDomainEventBus
import com.jstore.common.framework.event.outbox.EventSerializer
import com.jstore.common.framework.event.outbox.LocalDomainEventDeliveryChannel
import com.jstore.common.framework.event.outbox.OutboxDeadLetterOperationsRepository
import com.jstore.common.framework.event.outbox.OutboxDeliveryRouter
import com.jstore.common.framework.event.outbox.OutboxDeliveryTarget
import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxEntryRepository
import com.jstore.common.framework.event.outbox.OutboxEntryStatus
import com.jstore.common.framework.event.outbox.OutboxMessageKind
import com.jstore.common.framework.event.outbox.OutboxProperties
import com.jstore.common.framework.event.outbox.OutboxPublisher
import com.jstore.common.framework.event.outbox.SpringOutboxRelayTransactionOperations
import com.jstore.common.framework.messaging.MessageConsumptionRepository
import com.jstore.common.framework.messaging.persistence.MessageConsumptionRepositoryImpl
import com.jstore.common.framework.messaging.tryStart
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import javax.sql.DataSource
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(classes = [OutboxEntryRepositoryImplPostgresTest.TestConfig::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxEntryRepositoryImplPostgresTest {

    @Autowired private lateinit var repository: OutboxEntryRepository

    @Autowired private lateinit var jpaRepository: OutboxEntryPOJpaRepository

    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Autowired private lateinit var entityManager: EntityManager

    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var consumptionRepository: MessageConsumptionRepository

    @BeforeEach
    fun cleanDatabase() {
        jpaRepository.deleteAll()
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS outbox_tx_probe (id VARCHAR(64) PRIMARY KEY)"
                )
                statement.executeUpdate("DELETE FROM outbox_tx_probe")
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS outbox_listener_probe (event_id VARCHAR(64) PRIMARY KEY)"
                )
                statement.executeUpdate("DELETE FROM outbox_listener_probe")
                statement.executeUpdate("DELETE FROM outbox_dead_letter_audit")
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS domain_event_consumption (" +
                        "listener_id VARCHAR(512) NOT NULL, event_id VARCHAR(64) NOT NULL, " +
                        "event_name VARCHAR(256) NOT NULL, event_version INTEGER NOT NULL, " +
                        "consumed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(listener_id,event_id))"
                )
                statement.executeUpdate("DELETE FROM domain_event_consumption")
            }
        }
    }

    @Test
    fun `integration delivery routing metadata survives persistence round trip`() {
        val integrationEntry =
            entry("integration-routing")
                .copy(
                    messageKind = OutboxMessageKind.INTEGRATION_COMMAND,
                    deliveryTarget = OutboxDeliveryTarget.BROKER,
                    destination = "inventory.commands",
                    partitionKey = "order-42",
                    correlationId = "checkout-42",
                    causationId = "order-created-42",
                    tenantId = "merchant-7",
                )

        val saved =
            TransactionTemplate(transactionManager).execute { repository.save(integrationEntry) }!!

        assertEquals(OutboxMessageKind.INTEGRATION_COMMAND, saved.messageKind)
        assertEquals(OutboxDeliveryTarget.BROKER, saved.deliveryTarget)
        assertEquals("inventory.commands", saved.destination)
        assertEquals("order-42", saved.partitionKey)
        assertEquals("checkout-42", saved.correlationId)
        assertEquals("order-created-42", saved.causationId)
        assertEquals("merchant-7", saved.tenantId)
    }

    @Test
    fun `business row and outbox row commit and rollback atomically`() {
        val transactions = TransactionTemplate(transactionManager)
        transactions.executeWithoutResult {
            entityManager
                .createNativeQuery("INSERT INTO outbox_tx_probe(id) VALUES ('committed')")
                .executeUpdate()
            repository.save(entry("committed-event"))
        }

        runCatching {
            transactions.executeWithoutResult {
                entityManager
                    .createNativeQuery("INSERT INTO outbox_tx_probe(id) VALUES ('rolled-back')")
                    .executeUpdate()
                repository.save(entry("rolled-back-event"))
                error("simulate business failure")
            }
        }

        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT id FROM outbox_tx_probe ORDER BY id").use { rows ->
                    val ids = buildList { while (rows.next()) add(rows.getString(1)) }
                    assertEquals(listOf("committed"), ids)
                }
            }
        }
        assertTrue(jpaRepository.existsById("committed-event"))
        assertFalse(jpaRepository.existsById("rolled-back-event"))
    }

    @Test
    fun `relay commits listener side effect consumption receipt and published state in one transaction`() {
        repository.save(
            entry("relay-success", payload = "relay-success", aggregateId = "relay-success")
        )
        repository.save(
            entry(
                "relay-failure",
                payload = "relay-failure",
                aggregateId = "relay-failure",
                createdAt = Instant.now().plusMillis(1),
            )
        )
        val serializer =
            object : EventSerializer {
                override fun serialize(event: DomainEvent) = error("not used")

                override fun deserialize(
                    payload: String,
                    eventName: String,
                    eventVersion: Int,
                ): DomainEvent = RelayProbeEvent(payload)
            }
        val bus =
            object : LocalDomainEventBus {
                override fun publishEvent(domainEvent: DomainEvent) {
                    if (consumptionRepository.tryStart("relay-probe-listener", domainEvent)) {
                        entityManager
                            .createNativeQuery(
                                "INSERT INTO outbox_listener_probe(event_id) VALUES (:id)"
                            )
                            .setParameter("id", domainEvent.metadata.eventId)
                            .executeUpdate()
                        if (domainEvent.metadata.eventId == "relay-failure")
                            error("listener failed after side effect")
                    }
                }

                override fun register(domainEventListener: DomainEventListener<*>) = Unit

                override fun unregister(domainEventListener: DomainEventListener<*>) = Unit
            }
        OutboxPublisher(
                repository,
                OutboxDeliveryRouter(listOf(LocalDomainEventDeliveryChannel(serializer, bus))),
                OutboxProperties(batchSize = 10, workerId = "relay-e2e"),
                transactionOperations = SpringOutboxRelayTransactionOperations(transactionManager),
            )
            .pollAndPublish()

        assertEquals(
            OutboxEntryStatus.PUBLISHED,
            jpaRepository.findById("relay-success").orElseThrow().status,
        )
        assertEquals(
            OutboxEntryStatus.FAILED,
            jpaRepository.findById("relay-failure").orElseThrow().status,
        )
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery("SELECT event_id FROM outbox_listener_probe ORDER BY event_id")
                    .use { rows ->
                        val ids = buildList { while (rows.next()) add(rows.getString(1)) }
                        assertEquals(listOf("relay-success"), ids)
                    }
                statement
                    .executeQuery("SELECT event_id FROM domain_event_consumption ORDER BY event_id")
                    .use { rows ->
                        val ids = buildList { while (rows.next()) add(rows.getString(1)) }
                        assertEquals(listOf("relay-success"), ids)
                    }
            }
        }
    }

    private data class RelayProbeEvent(
        override val eventId: String,
        override val eventName: String = "relay.probe",
        override val eventVersion: Int = 1,
        override val occurredAt: Instant = Instant.now(),
        override val aggregateType: String = "RelayProbe",
        override val aggregateId: String = eventId,
    ) : DomainEvent

    @Test
    fun `claim locks rows and increments attempt count without duplicate sequential claims`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repository.save(entry("claim-1", createdAt = base))
        repository.save(entry("claim-2", createdAt = base.plusSeconds(1)))
        repository.save(entry("claim-3", createdAt = base.plusSeconds(2)))

        val firstClaim =
            repository.claimPendingAndRetryable(
                maxRetryCount = 5,
                batchSize = 2,
                lockedBy = "worker-a",
                lockedUntil = Instant.now().plusSeconds(60),
            )
        val secondClaim =
            repository.claimPendingAndRetryable(
                maxRetryCount = 5,
                batchSize = 2,
                lockedBy = "worker-b",
                lockedUntil = Instant.now().plusSeconds(60),
            )

        assertEquals(listOf("claim-1", "claim-2"), firstClaim.map { it.id })
        assertEquals(listOf("claim-3"), secondClaim.map { it.id })
        firstClaim.forEach {
            assertEquals(OutboxEntryStatus.IN_PROGRESS, it.status)
            assertEquals(1, it.retryCount)
            assertEquals("worker-a", it.lockedBy)
            assertNotNull(it.lockedUntil)
            assertEquals(1L, it.lockToken)
        }
    }

    @Test
    fun `concurrent workers never claim the same entry`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repeat(12) { index ->
            repository.save(
                entry(
                    "concurrent-$index",
                    createdAt = base.plusSeconds(index.toLong()),
                    aggregateId = "aggregate-$index",
                )
            )
        }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures =
                listOf("worker-a", "worker-b").map { worker ->
                    pool.submit<List<String>> {
                        start.await()
                        repository
                            .claimPendingAndRetryable(5, 12, worker, Instant.now().plusSeconds(60))
                            .map { it.id }
                    }
                }
            start.countDown()
            val claimed = futures.flatMap { it.get() }
            assertEquals(12, claimed.size)
            assertEquals(12, claimed.toSet().size)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent workers cannot overtake an in-flight event from the same aggregate`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repository.save(
            entry("same-aggregate-first", createdAt = base, aggregateId = "same-aggregate")
        )
        repository.save(
            entry(
                "same-aggregate-second",
                createdAt = base.plusSeconds(1),
                aggregateId = "same-aggregate",
            )
        )
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures =
                listOf("worker-a", "worker-b").map { worker ->
                    pool.submit<List<String>> {
                        start.await()
                        repository
                            .claimPendingAndRetryable(5, 2, worker, Instant.now().plusSeconds(60))
                            .map { it.id }
                    }
                }
            start.countDown()
            val claimed = futures.flatMap { it.get() }
            assertEquals(listOf("same-aggregate-first"), claimed)
            assertEquals(
                OutboxEntryStatus.PENDING,
                jpaRepository.findById("same-aggregate-second").orElseThrow().status,
            )
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `same aggregate cannot overtake while different aggregate can proceed`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repository.save(entry("order-1-first", createdAt = base, aggregateId = "order-1"))
        repository.save(
            entry("order-1-second", createdAt = base.plusSeconds(1), aggregateId = "order-1")
        )
        repository.save(
            entry("order-2-first", createdAt = base.plusSeconds(2), aggregateId = "order-2")
        )

        val firstClaim =
            repository.claimPendingAndRetryable(5, 10, "worker-a", Instant.now().plusSeconds(60))

        assertEquals(listOf("order-1-first", "order-2-first"), firstClaim.map { it.id })
        assertTrue(repository.markPublished(firstClaim.first(), "worker-a"))

        val nextClaim =
            repository.claimPendingAndRetryable(5, 10, "worker-b", Instant.now().plusSeconds(60))
        assertEquals(listOf("order-1-second"), nextClaim.map { it.id })
    }

    @Test
    fun `failed entries respect next attempt and expired locks are recovered until max retry`() {
        val now = Instant.now()
        repository.save(
            entry(
                id = "future-failed",
                status = OutboxEntryStatus.FAILED,
                retryCount = 1,
                nextAttemptAt = now.plusSeconds(60),
            )
        )
        repository.save(
            entry(
                id = "expired-lock",
                status = OutboxEntryStatus.IN_PROGRESS,
                retryCount = 4,
                lockedBy = "dead-worker",
                lockedAt = now.minusSeconds(120),
                lockedUntil = now.minusSeconds(60),
            )
        )
        repository.save(
            entry(
                id = "expired-exhausted",
                status = OutboxEntryStatus.IN_PROGRESS,
                retryCount = 5,
                lockedBy = "dead-worker",
                lockedAt = now.minusSeconds(120),
                lockedUntil = now.minusSeconds(60),
            )
        )

        val claimed =
            repository.claimPendingAndRetryable(
                maxRetryCount = 5,
                batchSize = 10,
                lockedBy = "worker-a",
                lockedUntil = now.plusSeconds(60),
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
        val claimed =
            repository
                .claimPendingAndRetryable(
                    maxRetryCount = 5,
                    batchSize = 1,
                    lockedBy = "worker-a",
                    lockedUntil = Instant.now().plusSeconds(60),
                )
                .single()

        val wrongOwnerUpdated =
            repository.markPublished(
                claimed.copy(updatedAt = Instant.now()),
                lockedBy = "worker-b",
            )
        assertFalse(wrongOwnerUpdated)
        assertEquals(
            OutboxEntryStatus.IN_PROGRESS,
            jpaRepository.findById("owner-check").orElseThrow().status,
        )

        val rightOwnerUpdated =
            repository.markPublished(
                claimed.copy(updatedAt = Instant.now()),
                lockedBy = "worker-a",
            )
        assertTrue(rightOwnerUpdated)
        assertEquals(
            OutboxEntryStatus.PUBLISHED,
            jpaRepository.findById("owner-check").orElseThrow().status,
        )
    }

    @Test
    fun `expired lease recovery fences the old token and current token can renew`() {
        val now = Instant.now()
        repository.save(
            entry(
                "fenced",
                status = OutboxEntryStatus.IN_PROGRESS,
                retryCount = 1,
                lockedBy = "old-worker",
                lockedAt = now.minusSeconds(120),
                lockedUntil = now.minusSeconds(60),
                lockToken = 7,
            )
        )

        val expired =
            jpaRepository.findById("fenced").orElseThrow().let {
                entry(
                    "fenced",
                    status = it.status,
                    retryCount = it.retryCount,
                    lockedBy = it.lockedBy,
                    lockedAt = it.lockedAt,
                    lockedUntil = it.lockedUntil,
                    lockToken = it.lockToken,
                )
            }
        assertFalse(repository.renewLease(expired.id, "old-worker", 7, now.plusSeconds(120)))
        assertFalse(repository.markPublished(expired, "old-worker"))

        val recovered =
            repository.claimPendingAndRetryable(5, 1, "new-worker", now.plusSeconds(60)).single()
        assertEquals(8L, recovered.lockToken)
        assertFalse(repository.renewLease(recovered.id, "old-worker", 7, now.plusSeconds(120)))
        assertFalse(repository.markPublished(recovered.copy(lockToken = 7), "new-worker"))
        assertTrue(repository.renewLease(recovered.id, "new-worker", 8, now.plusSeconds(120)))
        assertTrue(repository.markPublished(recovered, "new-worker"))
    }

    @Test
    fun `operational queries report oldest ready and expired locks`() {
        val now = Instant.now()
        repository.save(entry("ready-old", createdAt = now.minusSeconds(30)))
        repository.save(entry("ready-new", createdAt = now.minusSeconds(10)))
        repository.save(
            entry(
                "future",
                status = OutboxEntryStatus.FAILED,
                retryCount = 1,
                createdAt = now.minusSeconds(60),
                nextAttemptAt = now.plusSeconds(60),
            )
        )
        repository.save(
            entry(
                "expired",
                status = OutboxEntryStatus.IN_PROGRESS,
                retryCount = 1,
                createdAt = now.minusSeconds(20),
                lockedBy = "dead",
                lockedAt = now.minusSeconds(20),
                lockedUntil = now.minusSeconds(1),
            )
        )

        assertEquals(
            now.minusSeconds(30).toEpochMilli(),
            repository.findOldestReadyAt(now, 5)?.toEpochMilli(),
        )
        assertEquals(1L, repository.countExpiredLocks(now))
    }

    @Test
    fun `delete published before honors batch size`() {
        val old = Instant.parse("2025-01-01T00:00:00Z")
        repository.save(entry("old-1", status = OutboxEntryStatus.PUBLISHED, createdAt = old))
        repository.save(
            entry("old-2", status = OutboxEntryStatus.PUBLISHED, createdAt = old.plusSeconds(1))
        )
        repository.save(
            entry("old-3", status = OutboxEntryStatus.PUBLISHED, createdAt = old.plusSeconds(2))
        )
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
        repository.save(
            entry(
                "dead-1",
                status = OutboxEntryStatus.DEAD_LETTER,
                createdAt = base,
                updatedAt = base.plusSeconds(2),
                retryCount = 5,
            )
        )
        repository.save(
            entry(
                "dead-2",
                status = OutboxEntryStatus.DEAD_LETTER,
                createdAt = base,
                updatedAt = base.plusSeconds(1),
            )
        )
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
        assertEquals(0, dead1.retryCount)
        assertEquals(1L, repository.countByStatus(OutboxEntryStatus.DEAD_LETTER))
        assertEquals(2L, repository.countByStatus(OutboxEntryStatus.FAILED))

        val claimed =
            repository.claimPendingAndRetryable(
                maxRetryCount = 5,
                batchSize = 1,
                lockedBy = "recovery-worker",
                lockedUntil = Instant.now().plusSeconds(60),
            )
        assertEquals(listOf("dead-1"), claimed.map { it.id })
        assertEquals(1, claimed.single().retryCount)
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
        lockToken: Long = 0,
        aggregateId: String = id,
        lastError: String? = null,
        payload: String = """{"source":"test"}""",
    ) =
        OutboxEntry(
            id = id,
            eventType = "com.example.Event",
            payload = payload,
            aggregateType = "Order",
            aggregateId = aggregateId,
            status = status,
            createdAt = createdAt,
            updatedAt = updatedAt,
            retryCount = retryCount,
            nextAttemptAt = nextAttemptAt,
            lockedBy = lockedBy,
            lockedAt = lockedAt,
            lockedUntil = lockedUntil,
            lockToken = lockToken,
            lastError = lastError,
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

        @Bean
        fun messageConsumptionRepository(
            entityManager: EntityManager
        ): MessageConsumptionRepository = MessageConsumptionRepositoryImpl(entityManager)
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

    @Test
    fun `production dead-letter requeue resets budget and audits every requested target atomically`() {
        repository.save(
            entry("audited-dead", status = OutboxEntryStatus.DEAD_LETTER, retryCount = 5)
        )
        val operations = repository as OutboxDeadLetterOperationsRepository

        val result =
            operations.requeueDeadLetters(
                ids = listOf("audited-dead", "missing-entry"),
                operatorId = "admin-7",
                reason = "dependency recovered",
                nextAttemptAt = Instant.now(),
            )

        assertEquals(1, result.requeuedCount)
        assertEquals(1, result.notRequeuedCount)
        val claimed =
            repository
                .claimPendingAndRetryable(5, 1, "worker", Instant.now().plusSeconds(60))
                .single()
        assertEquals("audited-dead", claimed.id)
        assertEquals(1, claimed.retryCount)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT outbox_entry_id, operator_id, reason, result FROM outbox_dead_letter_audit ORDER BY id"
                    )
                    .use { rows ->
                        val audits = buildList {
                            while (rows.next()) add(
                                listOf(
                                    rows.getString(1),
                                    rows.getString(2),
                                    rows.getString(3),
                                    rows.getString(4),
                                )
                            )
                        }
                        assertEquals(
                            listOf(
                                listOf(
                                    "audited-dead",
                                    "admin-7",
                                    "dependency recovered",
                                    "REQUEUED",
                                ),
                                listOf(
                                    "missing-entry",
                                    "admin-7",
                                    "dependency recovered",
                                    "NOT_REQUEUED",
                                ),
                            ),
                            audits,
                        )
                    }
            }
        }
    }
}
