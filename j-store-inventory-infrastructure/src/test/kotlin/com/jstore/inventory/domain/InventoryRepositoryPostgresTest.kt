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
package com.jstore.inventory.domain

import com.jstore.common.utils.Success
import com.jstore.inventory.domain.persistence.StockPositionPOJpaRepository
import com.jstore.inventory.domain.persistence.StockReservationPOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class InventoryRepositoryPostgresTest {
    @Test
    fun `stock position and reservation survive a PostgreSQL round trip`() = database { factory ->
        transaction(factory) { entityManager ->
            val repositories = repositories(entityManager)
            repositories.positions.save(position())
            repositories.reservations.save(
                StockReservation(
                    StockReservationId("ORDER-51-SKU-41-NODE-DEFAULT"),
                    "ORDER-51-SKU-41-NODE-DEFAULT",
                    51,
                    "AUTH-51",
                    SkuId(41),
                    FulfillmentNodeId("DEFAULT"),
                    3,
                    Instant.parse("2026-08-10T02:00:00Z"),
                )
            )
            entityManager.flush()
            entityManager.clear()

            val restored =
                assertNotNull(
                    repositories.positions.findBySkuAndNode(
                        SkuId(41),
                        FulfillmentNodeId("DEFAULT"),
                    )
                )
            val reservation =
                assertNotNull(
                    repositories.reservations.findByBusinessKey("ORDER-51-SKU-41-NODE-DEFAULT")
                )
            assertEquals(10, restored.onHand)
            assertEquals(2, restored.reserved)
            assertEquals(3, reservation.quantity)
            assertEquals(
                listOf(reservation.id),
                repositories.reservations.findByOrderId(51).map { it.id },
            )
        }
    }

    @Test
    fun `position lock serializes reservations and prevents concurrent oversell`() =
        database { factory ->
            transaction(factory) { repositories(it).positions.save(position()) }
            val firstLocked = CountDownLatch(1)
            val allowFirstCommit = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val first =
                    pool.submit<Boolean> {
                        transaction(factory) { entityManager ->
                            val positions = repositories(entityManager).positions
                            val locked =
                                positions.lock(listOf(StockPositionId("41@DEFAULT"))).single()
                            val reserved = locked.reserve(7) is Success
                            positions.save(locked)
                            entityManager.flush()
                            firstLocked.countDown()
                            assertTrue(allowFirstCommit.await(5, TimeUnit.SECONDS))
                            reserved
                        }
                    }
                assertTrue(firstLocked.await(5, TimeUnit.SECONDS))
                val second =
                    pool.submit<Boolean> {
                        transaction(factory) { entityManager ->
                            secondStarted.countDown()
                            val positions = repositories(entityManager).positions
                            val locked =
                                positions.lock(listOf(StockPositionId("41@DEFAULT"))).single()
                            val reserved = locked.reserve(7) is Success
                            if (reserved) positions.save(locked)
                            reserved
                        }
                    }
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
                assertFailsWith<TimeoutException> { second.get(250, TimeUnit.MILLISECONDS) }
                allowFirstCommit.countDown()
                assertTrue(first.get(5, TimeUnit.SECONDS))
                assertEquals(false, second.get(5, TimeUnit.SECONDS))
                transaction(factory) {
                    assertEquals(
                        9,
                        repositories(it)
                            .positions
                            .findById(StockPositionId("41@DEFAULT"))
                            ?.reserved,
                    )
                }
            } finally {
                allowFirstCommit.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }

    private fun position() =
        StockPosition(
            StockPositionId("41@DEFAULT"),
            SkuId(41),
            FulfillmentNodeId("DEFAULT"),
            onHand = 10,
            reserved = 2,
        )

    private data class Repositories(
        val positions: StockPositionRepositoryImpl,
        val reservations: StockReservationRepositoryImpl,
    )

    private fun repositories(entityManager: EntityManager): Repositories {
        val factory = JpaRepositoryFactory(entityManager)
        return Repositories(
            StockPositionRepositoryImpl(
                factory.getRepository(StockPositionPOJpaRepository::class.java)
            ),
            StockReservationRepositoryImpl(
                factory.getRepository(StockReservationPOJpaRepository::class.java)
            ),
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
                    setPackagesToScan("com.jstore.inventory.domain.persistence")
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
