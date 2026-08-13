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
package com.jstore.outbox.spring.capacity

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jstore.outbox.OutboxDeliveryChannel
import com.jstore.outbox.OutboxDeliveryRouter
import com.jstore.outbox.OutboxEntry
import com.jstore.outbox.OutboxEntryRepository
import com.jstore.outbox.OutboxEntryStatus
import com.jstore.outbox.OutboxTransportIds
import com.jstore.outbox.spring.OutboxProperties
import com.jstore.outbox.spring.OutboxPublisher
import com.jstore.outbox.spring.OutboxRelayCoordinator
import com.jstore.outbox.spring.SpringOutboxRelayTransactionOperations
import com.jstore.outbox.spring.TransactionAwareOutboxRelaySignal
import com.jstore.outbox.spring.persistence.OutboxEntryPO
import com.jstore.outbox.spring.persistence.OutboxEntryPOJpaRepository
import com.jstore.outbox.spring.persistence.OutboxEntryRepositoryImpl
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
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
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

@Tag("capacity")
@SpringBootTest(classes = [OutboxRelayCapacityTest.TestConfig::class])
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OutboxRelayCapacityTest {
    @Autowired private lateinit var repository: OutboxEntryRepository
    @Autowired private lateinit var jpaRepository: OutboxEntryPOJpaRepository
    @Autowired private lateinit var transactionManager: PlatformTransactionManager

    @Test
    fun `measure transaction commit to local delivery latency`() {
        jpaRepository.deleteAll()
        val config = OutboxRelayCapacityConfig.fromSystemProperties()
        val committedAtNanos = ConcurrentHashMap<String, Long>()
        val latencies = ConcurrentLinkedQueue<Duration>()
        val delivered = CountDownLatch(config.messageCount)
        val relayExecutor = Executors.newSingleThreadExecutor()
        val producerExecutor = Executors.newFixedThreadPool(config.producerConcurrency)
        val channel =
            object : OutboxDeliveryChannel {
                override val transportId: String = OutboxTransportIds.LOCAL_DOMAIN

                override fun deliver(entry: OutboxEntry) {
                    val committedNanos =
                        checkNotNull(committedAtNanos[entry.id]) {
                            "delivery observed before commit timestamp: ${entry.id}"
                        }
                    latencies += Duration.ofNanos(System.nanoTime() - committedNanos)
                    delivered.countDown()
                }
            }
        val publisher =
            OutboxPublisher(
                repository,
                OutboxDeliveryRouter(listOf(channel)),
                OutboxProperties(
                    batchSize = config.batchSize,
                    maxInFlightPerPoll = config.batchSize,
                    maxBatchesPerDrain = config.maxBatchesPerDrain,
                    workerId = "capacity-relay",
                ),
                transactionOperations = SpringOutboxRelayTransactionOperations(transactionManager),
            )
        val coordinator = OutboxRelayCoordinator(publisher, relayExecutor)
        val signal = TransactionAwareOutboxRelaySignal(coordinator)
        val transactions = TransactionTemplate(transactionManager)
        val start = CountDownLatch(1)
        val startedAt = Instant.now()
        val startedNanos = System.nanoTime()

        try {
            val producers =
                (1..config.messageCount).map { index ->
                    producerExecutor.submit {
                        start.await()
                        val id = "capacity-$index"
                        transactions.executeWithoutResult {
                            repository.save(entry(id))
                            TransactionSynchronizationManager.registerSynchronization(
                                object : TransactionSynchronization {
                                    override fun afterCommit() {
                                        committedAtNanos[id] = System.nanoTime()
                                    }
                                }
                            )
                            signal.signalAfterCommit()
                        }
                    }
                }
            start.countDown()
            producers.forEach { it.get(config.timeout.toSeconds(), TimeUnit.SECONDS) }
            assertTrue(
                delivered.await(config.timeout.toMillis(), TimeUnit.MILLISECONDS),
                "relay timed out with ${delivered.count} messages undelivered",
            )
        } finally {
            producerExecutor.shutdownNow()
            relayExecutor.shutdown()
            relayExecutor.awaitTermination(10, TimeUnit.SECONDS)
        }

        val completedNanos = System.nanoTime()
        val completedAt = Instant.now()
        val report =
            OutboxRelayCapacityReport.create(
                config,
                latencies.toList(),
                startedAt,
                completedAt,
                Duration.ofNanos(completedNanos - startedNanos),
            )
        writeReport(report)

        assertEquals(config.messageCount, report.deliveredCount)
        assertEquals(
            config.messageCount.toLong(),
            repository.countByStatus(OutboxEntryStatus.PUBLISHED),
        )
        assertEquals(0L, repository.countByStatus(OutboxEntryStatus.FAILED))
        assertEquals(0L, repository.countByStatus(OutboxEntryStatus.DEAD_LETTER))
    }

    private fun entry(id: String): OutboxEntry {
        val now = Instant.now()
        return OutboxEntry(
            id = id,
            eventType = "capacity.probe",
            payload = "{}",
            aggregateType = "CapacityProbe",
            aggregateId = id,
            status = OutboxEntryStatus.PENDING,
            createdAt = now,
            updatedAt = now,
            orderingKey = "CapacityProbe:$id",
            sequenceNo = 1,
        )
    }

    private fun writeReport(report: OutboxRelayCapacityReport) {
        val configured = System.getProperty("outboxCapacity.output")
        val output =
            if (configured.isNullOrBlank()) {
                Path.of("build/reports/outbox-relay-capacity/result.json")
            } else {
                Path.of(configured)
            }
        Files.createDirectories(output.toAbsolutePath().parent)
        jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writerWithDefaultPrettyPrinter()
            .writeValue(output.toFile(), report)
        println("Outbox relay capacity report: ${output.toAbsolutePath()}")
    }

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
