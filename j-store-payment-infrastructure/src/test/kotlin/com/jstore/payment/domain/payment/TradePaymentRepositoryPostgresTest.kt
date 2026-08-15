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
package com.jstore.payment.domain.payment

import com.jstore.common.properties.Price
import com.jstore.payment.domain.payment.persistence.TradePaymentPOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class TradePaymentRepositoryPostgresTest {
    @Test
    fun `preparation cancellation barrier survives PostgreSQL round trip`() = database { factory ->
        transaction(factory) { entityManager ->
            val repository = repository(entityManager)
            val payment =
                TradePayment.prepare(
                    TradePaymentId(8101),
                    9002,
                    9902,
                    "FULL",
                    Price.ofFen(1000),
                    "CNY",
                    listOf(PaymentAllocationSnapshot(9201, 7101, 9, Price.ofFen(1000))),
                )
            payment.requestCancellation("buyer cancelled")
            repository.save(payment)
            entityManager.flush()
            entityManager.clear()

            val restored = assertNotNull(repository.findByInstallment(9902, "FULL"))
            assertEquals(TradePaymentStatus.PREPARATION_CANCELLING, restored.status)
            assertEquals("buyer cancelled", restored.cancellationReason)
            assertEquals(null, restored.providerReference)
        }
    }

    @Test
    fun `compensation identity and cancellation reason survive PostgreSQL round trip`() =
        database { factory ->
            transaction(factory) { entityManager ->
                val repository = repository(entityManager)
                val payment =
                    TradePayment.prepare(
                        TradePaymentId(8102),
                        9003,
                        9903,
                        "FULL",
                        Price.ofFen(1000),
                        "CNY",
                        listOf(PaymentAllocationSnapshot(9202, 7102, 9, Price.ofFen(1000))),
                    )
                payment.requestCancellation("buyer cancelled")
                val oversizedReference =
                    "provider-" + "r".repeat(TradePayment.MAX_PROVIDER_REFERENCE_LENGTH)
                payment.recordLateProviderAcceptance(
                    oversizedReference,
                    "a".repeat(TradePayment.MAX_PAY_ACTION_LENGTH + 1),
                    Instant.parse("2029-01-01T00:00:00Z"),
                    Instant.parse("2029-01-01T00:10:00Z"),
                    Instant.parse("2029-01-01T00:30:00Z"),
                )
                repository.save(payment)
                entityManager.flush()
                entityManager.clear()

                val restored = assertNotNull(repository.findByInstallment(9903, "FULL"))
                assertEquals(TradePaymentStatus.CANCELLING, restored.status)
                assertEquals(oversizedReference, restored.providerReference)
                assertEquals(null, restored.payAction)
                assertEquals("buyer cancelled", restored.cancellationReason)
            }
        }

    @Test
    fun `trade payment allocation survives PostgreSQL round trip`() = database { factory ->
        transaction(factory) { entityManager ->
            val repository = repository(entityManager)
            val providerReference = "r".repeat(TradePayment.MAX_PROVIDER_REFERENCE_LENGTH)
            val payAction = "a".repeat(TradePayment.MAX_PAY_ACTION_LENGTH)
            val payment =
                TradePayment.prepare(
                    TradePaymentId(8001),
                    9001,
                    9901,
                    "FULL",
                    Price.ofFen(3000),
                    "CNY",
                    listOf(
                        PaymentAllocationSnapshot(9101, 7001, 7, Price.ofFen(1000)),
                        PaymentAllocationSnapshot(9102, 7002, 8, Price.ofFen(2000)),
                    ),
                )
            val acceptedAt = Instant.parse("2029-01-01T00:00:00Z")
            payment.markReady(
                providerReference,
                payAction,
                acceptedAt,
                acceptedAt.plusSeconds(600),
                acceptedAt.plusSeconds(900),
            )
            repository.save(payment)
            entityManager.flush()
            entityManager.clear()

            val restored = assertNotNull(repository.findByInstallment(9901, "FULL"))
            assertEquals(9001, restored.tradeId)
            assertEquals(3000, restored.payableAmount.fen)
            assertEquals(listOf(1000L, 2000L), restored.allocations.map { it.amount.fen })
            assertEquals(TradePaymentStatus.READY, restored.status)
            assertEquals(providerReference, restored.providerReference)
            assertEquals(payAction, restored.payAction)
        }

        assertFails {
            transaction(factory) { entityManager ->
                repository(entityManager)
                    .save(
                        TradePayment.prepare(
                            TradePaymentId(8002),
                            9001,
                            9901,
                            "FULL",
                            Price.ofFen(3000),
                            "CNY",
                            listOf(
                                PaymentAllocationSnapshot(9101, 7001, 7, Price.ofFen(1000)),
                                PaymentAllocationSnapshot(9102, 7002, 8, Price.ofFen(2000)),
                            ),
                        )
                    )
                entityManager.flush()
            }
        }
    }

    private fun repository(entityManager: EntityManager): TradePaymentRepositoryImpl {
        val factory = JpaRepositoryFactory(entityManager)
        return TradePaymentRepositoryImpl(
            factory.getRepository(TradePaymentPOJpaRepository::class.java)
        )
    }

    private fun <T> transaction(factory: EntityManagerFactory, block: (EntityManager) -> T): T {
        val entityManager = factory.createEntityManager()
        return try {
            entityManager.transaction.begin()
            val result = block(entityManager)
            entityManager.transaction.commit()
            result
        } catch (throwable: Throwable) {
            if (entityManager.transaction.isActive) entityManager.transaction.rollback()
            throw throwable
        } finally {
            entityManager.close()
        }
    }

    private fun database(block: (EntityManagerFactory) -> Unit) {
        EmbeddedPostgres.builder().start().use { postgres ->
            val factoryBean =
                LocalContainerEntityManagerFactoryBean().apply {
                    dataSource = postgres.postgresDatabase
                    jpaVendorAdapter = HibernateJpaVendorAdapter()
                    setPackagesToScan("com.jstore.payment.domain.payment.persistence")
                    setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "create-drop"))
                    afterPropertiesSet()
                }
            try {
                block(requireNotNull(factoryBean.`object`))
            } finally {
                factoryBean.destroy()
            }
        }
    }
}
