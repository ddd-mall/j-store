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
package com.jstore.warehouse.domain

import com.jstore.common.utils.Success
import com.jstore.warehouse.domain.persistence.PhysicalStockPOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class PhysicalStockRepositoryPostgresTest {
    @Test
    fun `physical stock survives a PostgreSQL round trip`() = database { factory ->
        transaction(factory) { entityManager ->
            val repository = repository(entityManager)
            repository.save(PhysicalStock(PhysicalStockId("61@WH-1"), 61, "WH-1", 20, 3))
            entityManager.flush()
            entityManager.clear()
            val restored = assertNotNull(repository.findById(PhysicalStockId("61@WH-1")))
            assertEquals(20, restored.onHand)
            assertEquals(3, restored.sourceVersion)
        }
    }

    @Test
    fun `optimistic version allows one concurrent stale update`() = database { factory ->
        transaction(factory) {
            repository(it).save(PhysicalStock(PhysicalStockId("61@WH-1"), 61, "WH-1", 20, 3))
        }
        val loaded = CountDownLatch(2)
        val update = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                listOf(21, 22).map { quantity ->
                    pool.submit<Boolean> {
                        try {
                            transaction(factory) { entityManager ->
                                val stocks = repository(entityManager)
                                val stock =
                                    assertNotNull(stocks.findById(PhysicalStockId("61@WH-1")))
                                loaded.countDown()
                                assertTrue(update.await(5, TimeUnit.SECONDS))
                                assertTrue(stock.adjustTo(quantity, "count") is Success)
                                stocks.save(stock)
                                entityManager.flush()
                            }
                            true
                        } catch (_: RuntimeException) {
                            false
                        }
                    }
                }
            assertTrue(loaded.await(5, TimeUnit.SECONDS))
            update.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
            transaction(factory) {
                val current = assertNotNull(repository(it).findById(PhysicalStockId("61@WH-1")))
                assertTrue(current.onHand == 21 || current.onHand == 22)
                assertEquals(4, current.sourceVersion)
            }
        } finally {
            update.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    private fun repository(entityManager: EntityManager) =
        PhysicalStockRepositoryImpl(
            JpaRepositoryFactory(entityManager)
                .getRepository(PhysicalStockPOJpaRepository::class.java)
        )

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
                    setPackagesToScan("com.jstore.warehouse.domain.persistence")
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
