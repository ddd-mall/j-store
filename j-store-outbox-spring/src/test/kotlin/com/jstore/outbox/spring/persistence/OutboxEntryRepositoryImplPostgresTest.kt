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
package com.jstore.outbox.spring.persistence

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.LocalDomainEventBus
import com.jstore.messaging.BuiltInMessageConsumerIds
import com.jstore.messaging.MessageConsumptionRepository
import com.jstore.messaging.MessageConsumptionRetentionRepository
import com.jstore.messaging.MessageDeliveryOrder
import com.jstore.messaging.MessageSequenceGapException
import com.jstore.messaging.tryStart
import com.jstore.outbox.*
import com.jstore.outbox.spring.*
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

    @Autowired
    private lateinit var consumptionRetentionRepository: MessageConsumptionRetentionRepository

    @Autowired private lateinit var streamSequenceAllocator: OutboxStreamSequenceAllocator

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
                    "CREATE TABLE IF NOT EXISTS outbox_stream_position (" +
                        "transport_id VARCHAR(64) NOT NULL, ordering_key VARCHAR(64) NOT NULL, " +
                        "last_sequence_no BIGINT NOT NULL, PRIMARY KEY(transport_id, ordering_key))"
                )
                statement.executeUpdate("DELETE FROM outbox_stream_position")
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS domain_event_consumption (" +
                        "listener_id VARCHAR(512) NOT NULL, event_id VARCHAR(64) NOT NULL, " +
                        "event_name VARCHAR(256) NOT NULL, event_version INTEGER NOT NULL, " +
                        "consumed_at TIMESTAMPTZ NOT NULL, PRIMARY KEY(listener_id,event_id))"
                )
                statement.executeUpdate("DELETE FROM domain_event_consumption")
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS message_stream_consumption (" +
                        "consumer_id VARCHAR(512) NOT NULL, transport_id VARCHAR(64) NOT NULL, " +
                        "ordering_key VARCHAR(64) NOT NULL, " +
                        "last_sequence_no BIGINT NOT NULL, updated_at TIMESTAMPTZ NOT NULL, " +
                        "PRIMARY KEY(consumer_id, transport_id, ordering_key))"
                )
                statement.executeUpdate("DELETE FROM message_stream_consumption")
            }
        }
    }

    @Test
    fun `integration delivery routing metadata survives persistence round trip`() {
        val acceptBefore = Instant.parse("2099-01-01T00:00:00Z")
        val integrationEntry =
            entry("integration-routing")
                .copy(
                    messageKind = OutboxMessageKind.INTEGRATION_COMMAND,
                    deliveryTarget = OutboxDeliveryTarget.BROKER,
                    transportId = "kafka",
                    destination = "commerce.inventory.commands.v1",
                    logicalDestination = "inventory.commands",
                    deliveryProfile = "CHECKOUT_CRITICAL",
                    acceptBefore = acceptBefore,
                    partitionKey = "order-42",
                    correlationId = "checkout-42",
                    causationId = "order-created-42",
                    merchantScopeId = "merchant-7",
                    deploymentScopeId = "site-jp",
                    orderingKey = "inventory.commands:order-42",
                    sequenceNo = 17,
                )

        val saved =
            TransactionTemplate(transactionManager).execute { repository.save(integrationEntry) }!!

        assertEquals(OutboxMessageKind.INTEGRATION_COMMAND, saved.messageKind)
        assertEquals(OutboxDeliveryTarget.BROKER, saved.deliveryTarget)
        assertEquals("kafka", saved.transportId)
        assertEquals("commerce.inventory.commands.v1", saved.destination)
        assertEquals("inventory.commands", saved.logicalDestination)
        assertEquals("CHECKOUT_CRITICAL", saved.deliveryProfile)
        assertEquals(acceptBefore, saved.acceptBefore)
        assertEquals(null, saved.publishedAt)
        assertEquals("order-42", saved.partitionKey)
        assertEquals("checkout-42", saved.correlationId)
        assertEquals("order-created-42", saved.causationId)
        assertEquals("merchant-7", saved.merchantScopeId)
        assertEquals("site-jp", saved.deploymentScopeId)
        assertEquals("inventory.commands:order-42", saved.orderingKey)
        assertEquals(17, saved.sequenceNo)
    }

    @Test
    fun `stream sequence allocation is monotonic and isolated by transport`() {
        val transactions = TransactionTemplate(transactionManager)

        val local =
            transactions.execute {
                listOf(
                    streamSequenceAllocator.nextSequence("local", "Order:42"),
                    streamSequenceAllocator.nextSequence("local", "Order:42"),
                )
            }!!
        val kafka =
            transactions.execute {
                streamSequenceAllocator.nextSequence("kafka", "Order:42")
            }!!

        assertEquals(listOf(1L, 2L), local)
        assertEquals(1L, kafka)
    }

    @Test
    fun `batch stream sequence allocation reserves continuous ranges in input order`() {
        val transactions = TransactionTemplate(transactionManager)
        val orderStream = OutboxStreamKey("local-domain", "Order:batch")
        val paymentStream = OutboxStreamKey("local-domain", "Payment:batch")

        val allocated =
            transactions.execute {
                streamSequenceAllocator.nextSequences(
                    listOf(orderStream, paymentStream, orderStream, orderStream, paymentStream)
                )
            }!!

        assertEquals(listOf(1L, 1L, 2L, 3L, 2L), allocated)
        assertEquals(
            listOf(4L, 3L),
            transactions.execute {
                streamSequenceAllocator.nextSequences(listOf(orderStream, paymentStream))
            }!!,
        )
    }

    @Test
    fun `batch save persists every entry in input order`() {
        val entries =
            listOf(
                entry("batch-save-1", orderingKey = "Order:batch-save", sequenceNo = 1),
                entry("batch-save-2", orderingKey = "Order:batch-save", sequenceNo = 2),
            )

        val saved =
            TransactionTemplate(transactionManager).execute { repository.saveAll(entries) }!!

        assertEquals(entries.map { it.id }, saved.map { it.id })
        assertEquals(entries.map { it.id }.toSet(), jpaRepository.findAll().map { it.id }.toSet())
    }

    @Test
    fun `ordered consumption accepts only the next sequence and rolls back gaps`() {
        val transactions = TransactionTemplate(transactionManager)
        assertTrue(
            transactions.execute {
                consumptionRepository.tryStartOrdered(
                    "consumer-1",
                    "message-1",
                    "order.event",
                    1,
                    MessageDeliveryOrder("local", "Order:42", 1),
                )
            }!!
        )

        kotlin.test.assertFailsWith<MessageSequenceGapException> {
            transactions.execute {
                consumptionRepository.tryStartOrdered(
                    "consumer-1",
                    "message-3",
                    "order.event",
                    1,
                    MessageDeliveryOrder("local", "Order:42", 3),
                )
            }
        }

        assertTrue(
            transactions.execute {
                consumptionRepository.tryStartOrdered(
                    "consumer-1",
                    "message-2",
                    "order.event",
                    1,
                    MessageDeliveryOrder("local", "Order:42", 2),
                )
            }!!
        )
        assertFalse(
            transactions.execute {
                consumptionRepository.tryStartOrdered(
                    "consumer-1",
                    "message-2",
                    "order.event",
                    1,
                    MessageDeliveryOrder("local", "Order:42", 2),
                )
            }!!
        )
    }

    @Test
    fun `new ordered consumer rejects a first message that skips the stream prefix`() {
        kotlin.test.assertFailsWith<MessageSequenceGapException> {
            TransactionTemplate(transactionManager).execute {
                consumptionRepository.tryStartOrdered(
                    "new-broker-consumer",
                    "message-2",
                    "order.event",
                    1,
                    MessageDeliveryOrder("kafka", "Order:new-consumer", 2),
                )
            }
        }
    }

    @Test
    fun `local ordered consumer cannot skip an unfinished predecessor without a cursor`() {
        repository.save(
            entry(
                id = "pending-1",
                status = OutboxEntryStatus.FAILED,
                transportId = OutboxTransportIds.LOCAL,
                orderingKey = "Order:missing-cursor",
                sequenceNo = 1,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
            )
        )

        kotlin.test.assertFailsWith<MessageSequenceGapException> {
            TransactionTemplate(transactionManager).execute {
                consumptionRepository.tryStartOrdered(
                    BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS,
                    "message-2",
                    "order.event",
                    1,
                    MessageDeliveryOrder(
                        OutboxTransportIds.LOCAL,
                        "Order:missing-cursor",
                        2,
                    ),
                )
            }
        }
    }

    @Test
    fun `retention deletes old consumption details in bounded batches`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                repeat(3) { index ->
                    statement.executeUpdate(
                        "INSERT INTO domain_event_consumption " +
                            "(listener_id, event_id, event_name, event_version, consumed_at) " +
                            "VALUES ('listener', 'old-$index', 'order.event', 1, " +
                            "TIMESTAMPTZ '2025-01-01 00:00:00+00')"
                    )
                }
                statement.executeUpdate(
                    "INSERT INTO domain_event_consumption " +
                        "(listener_id, event_id, event_name, event_version, consumed_at) " +
                        "VALUES ('listener', 'recent', 'order.event', 1, NOW())"
                )
            }
        }

        val deleted =
            consumptionRetentionRepository.deleteConsumptionsBefore(
                Instant.parse("2026-01-01T00:00:00Z"),
                2,
            )

        assertEquals(2, deleted)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM domain_event_consumption").use {
                    it.next()
                    assertEquals(2, it.getInt(1))
                }
            }
        }
    }

    @Test
    fun `retention keeps active stream and restores an idle stream from its next actual sequence`() {
        val old = "TIMESTAMPTZ '2025-01-01 00:00:00+00'"
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO message_stream_consumption " +
                        "(consumer_id, transport_id, ordering_key, last_sequence_no, updated_at) " +
                        "VALUES ('${BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS}', " +
                        "'local', 'Order:idle', 8, $old), " +
                        "('${BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS}', " +
                        "'local', 'Order:active', 8, $old)"
                )
            }
        }
        repository.save(
            entry(
                id = "active-9",
                status = OutboxEntryStatus.FAILED,
                transportId = "local",
                orderingKey = "Order:active",
                sequenceNo = 9,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
            )
        )

        val deleted =
            consumptionRetentionRepository.deleteInactiveStreamPositionsBefore(
                Instant.parse("2026-01-01T00:00:00Z"),
                10,
            )

        assertEquals(1, deleted)
        val accepted =
            TransactionTemplate(transactionManager).execute {
                consumptionRepository.tryStartOrdered(
                    BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS,
                    "idle-9",
                    "order.event",
                    1,
                    MessageDeliveryOrder("local", "Order:idle", 9),
                )
            }
        assertTrue(accepted!!)
    }

    @Test
    fun `retention keeps external consumer cursors without a local producer watermark`() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO message_stream_consumption " +
                        "(consumer_id, transport_id, ordering_key, last_sequence_no, updated_at) " +
                        "VALUES ('broker-consumer', 'kafka', 'Order:external', 8, " +
                        "TIMESTAMPTZ '2025-01-01 00:00:00+00')"
                )
            }
        }

        val deleted =
            consumptionRetentionRepository.deleteInactiveStreamPositionsBefore(
                Instant.parse("2026-01-01T00:00:00Z"),
                10,
            )

        assertEquals(0, deleted)
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT last_sequence_no FROM message_stream_consumption " +
                            "WHERE consumer_id = 'broker-consumer'"
                    )
                    .use { rows ->
                        assertTrue(rows.next())
                        assertEquals(8, rows.getLong(1))
                    }
            }
        }
    }

    @Test
    fun `ordered consumption catches up across records published before ordering migration`() {
        val transportId = "local"
        val orderingKey = "Order:migrated"
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    "INSERT INTO outbox_stream_position " +
                        "(transport_id, ordering_key, last_sequence_no) " +
                        "VALUES ('$transportId', '$orderingKey', 4)"
                )
                statement.executeUpdate(
                    "INSERT INTO message_stream_consumption " +
                        "(consumer_id, transport_id, ordering_key, last_sequence_no, updated_at) " +
                        "VALUES ('${BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS}', " +
                        "'$transportId', '$orderingKey', 1, NOW())"
                )
            }
        }
        repository.save(
            entry(
                id = "migrated-2",
                status = OutboxEntryStatus.FAILED,
                transportId = transportId,
                orderingKey = orderingKey,
                sequenceNo = 2,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
            )
        )
        repository.save(
            entry(
                id = "migrated-3",
                status = OutboxEntryStatus.PUBLISHED,
                transportId = transportId,
                orderingKey = orderingKey,
                sequenceNo = 3,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
            )
        )
        repository.save(
            entry(
                id = "migrated-4",
                transportId = transportId,
                orderingKey = orderingKey,
                sequenceNo = 4,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
            )
        )

        val transactions = TransactionTemplate(transactionManager)
        assertTrue(
            transactions.execute {
                consumptionRepository.tryStartOrdered(
                    BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS,
                    "message-2",
                    "order.event",
                    1,
                    MessageDeliveryOrder(transportId, orderingKey, 2),
                )
            }!!
        )
        assertTrue(
            transactions.execute {
                consumptionRepository.tryStartOrdered(
                    BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS,
                    "message-4",
                    "order.event",
                    1,
                    MessageDeliveryOrder(transportId, orderingKey, 4),
                )
            }!!
        )
    }

    @Test
    fun `ordered consumption isolates identical ordering keys by transport`() {
        val transactions = TransactionTemplate(transactionManager)

        val accepted =
            listOf("local", "kafka").map { transportId ->
                transactions.execute {
                    consumptionRepository.tryStartOrdered(
                        "consumer-1",
                        "shared-message",
                        "order.event",
                        1,
                        MessageDeliveryOrder(transportId, "Order:42", 1),
                    )
                }!!
            }
        assertEquals(listOf(true, false), accepted)
        assertTrue(
            transactions.execute {
                consumptionRepository.tryStartOrdered(
                    "consumer-1",
                    "kafka-message-2",
                    "order.event",
                    1,
                    MessageDeliveryOrder("kafka", "Order:42", 2),
                )
            }!!
        )
    }

    @Test
    fun `concurrent stream sequence allocation never duplicates or leaves a gap`() {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures =
                (1..16).map {
                    pool.submit<Long> {
                        start.await()
                        TransactionTemplate(transactionManager).execute {
                            streamSequenceAllocator.nextSequence("kafka", "Order:concurrent")
                        }!!
                    }
                }
            start.countDown()

            assertEquals((1L..16L).toList(), futures.map { it.get() }.sorted())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `concurrent batch sequence allocation reserves non-overlapping ranges`() {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(8)
        val stream = OutboxStreamKey("kafka", "Order:concurrent-batch")
        try {
            val futures =
                (1..8).map {
                    pool.submit<List<Long>> {
                        start.await()
                        TransactionTemplate(transactionManager).execute {
                            streamSequenceAllocator.nextSequences(listOf(stream, stream, stream))
                        }!!
                    }
                }
            start.countDown()

            assertEquals((1L..24L).toList(), futures.flatMap { it.get() }.sorted())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `rolled back sequence allocation is reusable by the next committed publication`() {
        runCatching {
            TransactionTemplate(transactionManager).executeWithoutResult {
                assertEquals(
                    1L,
                    streamSequenceAllocator.nextSequence("kafka", "Order:rollback"),
                )
                error("rollback")
            }
        }

        val committed =
            TransactionTemplate(transactionManager).execute {
                streamSequenceAllocator.nextSequence("kafka", "Order:rollback")
            }

        assertEquals(1L, committed!!)
    }

    @Test
    fun `rolled back batch sequence range is reusable by the next publication`() {
        val stream = OutboxStreamKey("kafka", "Order:batch-rollback")
        runCatching {
            TransactionTemplate(transactionManager).executeWithoutResult {
                assertEquals(
                    listOf(1L, 2L, 3L),
                    streamSequenceAllocator.nextSequences(listOf(stream, stream, stream)),
                )
                error("rollback")
            }
        }

        val committed =
            TransactionTemplate(transactionManager).execute {
                streamSequenceAllocator.nextSequences(listOf(stream, stream))
            }

        assertEquals(listOf(1L, 2L), committed!!)
    }

    @Test
    fun `failed stream blocks only its own transport and ordering key`() {
        val base = Instant.parse("2026-01-01T00:00:00Z")
        repository.save(
            entry(
                "kafka-first",
                status = OutboxEntryStatus.DEAD_LETTER,
                createdAt = base,
                aggregateId = "order-1",
                transportId = "kafka",
                orderingKey = "Order:order-1",
                sequenceNo = 1,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
            )
        )
        repository.save(
            entry(
                "kafka-second",
                createdAt = base.plusSeconds(1),
                aggregateId = "order-1",
                transportId = "kafka",
                orderingKey = "Order:order-1",
                sequenceNo = 2,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
            )
        )
        repository.save(
            entry(
                "local-same-order",
                createdAt = base.plusSeconds(2),
                aggregateId = "order-1",
                transportId = "local",
                orderingKey = "Order:order-1",
                sequenceNo = 1,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
            )
        )
        repository.save(
            entry(
                "kafka-other-order",
                createdAt = base.plusSeconds(3),
                aggregateId = "order-2",
                transportId = "kafka",
                orderingKey = "Order:order-2",
                sequenceNo = 1,
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
            )
        )

        val claimed =
            repository.claimPendingAndRetryable(
                5,
                10,
                "worker",
                Instant.now().plusSeconds(60),
            )

        assertEquals(
            setOf("local-same-order", "kafka-other-order"),
            claimed.map { it.id }.toSet(),
        )
        assertEquals(
            OutboxEntryStatus.PENDING,
            jpaRepository.findById("kafka-second").orElseThrow().status,
        )
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

    @Test
    fun `prepared delivery retries failures and atomically commits after fencing succeeds`() {
        repository.save(
            entry("prepared-probe", payload = "prepared-probe", aggregateId = "prepared-probe")
        )
        var stage = 0
        val serializer =
            object : EventSerializer {
                override fun serialize(event: DomainEvent) = error("unused")

                override fun deserialize(
                    payload: String,
                    eventName: String,
                    eventVersion: Int,
                ): DomainEvent = RelayProbeEvent(payload)
            }
        org.springframework.context.support.GenericApplicationContext().use { context ->
            context.refresh()
            val registry =
                com.jstore.messaging.local.event.SpringDomainEventListenerRegistry(
                    context,
                    consumptionRepository,
                )
            registry.register(
                object :
                    com.jstore.common.framework.event.PreparingDomainEventListener<
                        RelayProbeEvent
                    > {
                    override fun listenerId() = "prepared-probe-listener"

                    override fun onDomainEvent(event: RelayProbeEvent) =
                        error("must use preparation")

                    override fun prepare(event: RelayProbeEvent): () -> Unit {
                        assertFalse(
                            org.springframework.transaction.support
                                .TransactionSynchronizationManager
                                .isActualTransactionActive()
                        )
                        assertFalse(
                            org.springframework.transaction.support
                                .TransactionSynchronizationManager
                                .hasResource(dataSource)
                        )
                        if (stage == 0) error("upstream unavailable")
                        return {
                            assertTrue(
                                org.springframework.transaction.support
                                    .TransactionSynchronizationManager
                                    .isActualTransactionActive()
                            )
                            entityManager
                                .createNativeQuery(
                                    "INSERT INTO outbox_listener_probe(event_id) VALUES (:id)"
                                )
                                .setParameter("id", event.eventId)
                                .executeUpdate()
                            if (stage == 1) error("completion failed")
                        }
                    }
                }
            )
            val bus = com.jstore.messaging.local.event.SpringLocalDomainEventBus(registry, context)
            val fencedRepository =
                object : OutboxEntryRepository by repository {
                    override fun markPublished(entry: OutboxEntry, lockedBy: String): Boolean =
                        if (stage == 2) false else repository.markPublished(entry, lockedBy)
                }
            val publisher =
                OutboxPublisher(
                    fencedRepository,
                    OutboxDeliveryRouter(listOf(LocalDomainEventDeliveryChannel(serializer, bus))),
                    OutboxProperties(batchSize = 1, maxRetryCount = 5),
                    transactionOperations =
                        SpringOutboxRelayTransactionOperations(transactionManager),
                )
            for (attempt in 0..3) {
                stage = attempt
                TransactionTemplate(transactionManager).executeWithoutResult {
                    entityManager
                        .createNativeQuery(
                            "UPDATE outbox_entry SET next_attempt_at = :now WHERE id = 'prepared-probe'"
                        )
                        .setParameter("now", Instant.now().minusSeconds(1))
                        .executeUpdate()
                }
                publisher.pollAndPublish()
                assertEquals(
                    if (attempt == 3) OutboxEntryStatus.PUBLISHED else OutboxEntryStatus.FAILED,
                    jpaRepository.findById("prepared-probe").orElseThrow().status,
                )
                dataSource.connection.use { connection ->
                    connection.createStatement().use { statement ->
                        for (table in listOf("outbox_listener_probe", "domain_event_consumption")) {
                            statement.executeQuery("SELECT count(*) FROM $table").use { rows ->
                                rows.next()
                                assertEquals(if (attempt == 3) 1 else 0, rows.getInt(1))
                            }
                        }
                    }
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
                sequenceNo = 2,
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
            entry(
                "order-1-second",
                createdAt = base.plusSeconds(1),
                aggregateId = "order-1",
                sequenceNo = 2,
            )
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
    fun `operational queries isolate backlog and failures by transport`() {
        val now = Instant.now()
        repository.save(
            entry(
                "local-ready",
                createdAt = now.minusSeconds(30),
                transportId = "local",
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.LOCAL_INTEGRATION,
            )
        )
        repository.save(
            entry(
                "kafka-dead",
                status = OutboxEntryStatus.DEAD_LETTER,
                createdAt = now.minusSeconds(300),
                transportId = "kafka",
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
            )
        )
        repository.save(
            entry(
                "kafka-expired",
                status = OutboxEntryStatus.IN_PROGRESS,
                retryCount = 1,
                createdAt = now.minusSeconds(120),
                lockedBy = "dead-worker",
                lockedAt = now.minusSeconds(60),
                lockedUntil = now.minusSeconds(1),
                transportId = "kafka",
                orderingKey = "Order:kafka-expired",
                messageKind = OutboxMessageKind.INTEGRATION_EVENT,
                deliveryTarget = OutboxDeliveryTarget.BROKER,
            )
        )

        assertEquals(setOf("kafka", "local"), repository.findTransportIds())
        assertEquals(
            now.minusSeconds(30).toEpochMilli(),
            repository.findOldestReadyAt(now, 5, "local")?.toEpochMilli(),
        )
        assertEquals(
            now.minusSeconds(120).toEpochMilli(),
            repository.findOldestReadyAt(now, 5, "kafka")?.toEpochMilli(),
        )
        assertEquals(0L, repository.countExpiredLocks(now, "local"))
        assertEquals(1L, repository.countExpiredLocks(now, "kafka"))
        assertEquals(0L, repository.countByStatus(OutboxEntryStatus.DEAD_LETTER, "local"))
        assertEquals(1L, repository.countByStatus(OutboxEntryStatus.DEAD_LETTER, "kafka"))
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
    fun `dead letters can be queried and counted`() {
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
        transportId: String = OutboxTransportIds.LOCAL_DOMAIN,
        orderingKey: String = "Order:$aggregateId",
        sequenceNo: Long = 1,
        messageKind: OutboxMessageKind = OutboxMessageKind.DOMAIN_EVENT,
        deliveryTarget: OutboxDeliveryTarget = OutboxDeliveryTarget.LOCAL_DOMAIN,
        publishedAt: Instant? = createdAt.takeIf { status == OutboxEntryStatus.PUBLISHED },
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
            transportId = transportId,
            orderingKey = orderingKey,
            sequenceNo = sequenceNo,
            messageKind = messageKind,
            deliveryTarget = deliveryTarget,
            publishedAt = publishedAt,
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
        ): MessageConsumptionRepositoryImpl = MessageConsumptionRepositoryImpl(entityManager)

        @Bean
        fun outboxStreamSequenceAllocator(
            entityManager: EntityManager
        ): OutboxStreamSequenceAllocator = PostgresOutboxStreamSequenceAllocator(entityManager)
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
