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
package com.jstore.trade.domain

import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import com.jstore.trade.domain.persistence.TradeProcessPOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class TradeProcessRepositoryPostgresTest {
    @Test
    fun `committed trade process survives PostgreSQL round trip`() =
        database { entityManagerFactory ->
            transaction(entityManagerFactory) { entityManager ->
                val repository = repository(entityManager)
                val expiresAt = Instant.parse("2026-08-14T12:00:00Z")
                val trade =
                    TradeProcess.start(
                        TradeProcessId(100),
                        100,
                        7,
                        listOf(
                            TradeItemSnapshot(
                                10,
                                3,
                                20,
                                21,
                                2,
                                4,
                                5,
                                "CN-NORTH-1",
                                "ONLINE",
                                Price.ofFen(990),
                            )
                        ),
                        Price.ofFen(1980),
                        "CNY",
                    )
                assertIs<Success<Boolean>>(
                    trade.recordSaleAuthorized(listOf(TradeAuthorization("auth-1", 10, expiresAt)))
                )
                assertIs<Success<Boolean>>(
                    trade.recordInventoryReserved(
                        listOf("reservation-1"),
                        expiresAt.minusSeconds(10),
                    )
                )

                repository.save(trade)
                entityManager.flush()
                entityManager.clear()

                val restored = assertNotNull(repository.findById(TradeProcessId(100)))
                assertEquals(TradeProcessStatus.COMMITTED, restored.status)
                assertEquals(listOf("auth-1"), restored.authorizations.map { it.authorizationId })
                assertEquals(listOf("reservation-1"), restored.reservationIds)
                assertEquals(1980, restored.payableAmount.fen)
            }
        }

    private fun repository(entityManager: EntityManager): TradeProcessRepositoryImpl {
        val factory = JpaRepositoryFactory(entityManager)
        return TradeProcessRepositoryImpl(
            factory.getRepository(TradeProcessPOJpaRepository::class.java)
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
                    setPackagesToScan("com.jstore.trade.domain.persistence")
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
