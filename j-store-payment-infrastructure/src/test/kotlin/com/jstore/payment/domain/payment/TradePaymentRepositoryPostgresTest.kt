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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class TradePaymentRepositoryPostgresTest {
    @Test
    fun `trade payment allocation survives PostgreSQL round trip`() = database { factory ->
        transaction(factory) { entityManager ->
            val repository = repository(entityManager)
            repository.save(
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
            )
            entityManager.flush()
            entityManager.clear()

            val restored = assertNotNull(repository.findByInstallment(9901, "FULL"))
            assertEquals(9001, restored.tradeId)
            assertEquals(3000, restored.payableAmount.fen)
            assertEquals(listOf(1000L, 2000L), restored.allocations.map { it.amount.fen })
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
