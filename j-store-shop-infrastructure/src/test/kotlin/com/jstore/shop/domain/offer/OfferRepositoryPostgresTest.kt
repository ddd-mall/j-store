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
package com.jstore.shop.domain.offer

import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import com.jstore.shop.domain.offer.persistence.SaleAuthorizationPOJpaRepository
import com.jstore.shop.domain.offer.persistence.SalesOfferPOJpaRepository
import com.jstore.shop.domain.offer.persistence.StorePOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class OfferRepositoryPostgresTest {
    @Test
    fun `store offer and authorization survive a PostgreSQL round trip`() = database { factory ->
        transaction(factory) { entityManager ->
            val repositories = repositories(entityManager)
            val now = Instant.parse("2026-08-10T01:00:00Z")
            repositories.stores.save(
                Store(StoreId(11), MerchantId(21), "Main Store", StoreStatus.ACTIVE)
            )
            repositories.offers.save(offer(now))
            repositories.authorizations.save(
                assertIs<Success<SaleAuthorization>>(
                        offer(now).authorize(301, 31, 2, 1299, now, 1, Duration.ofMinutes(10))
                    )
                    .value
            )
            entityManager.flush()
            entityManager.clear()

            val restoredStore = assertNotNull(repositories.stores.findById(StoreId(11)))
            val restoredOffer = assertNotNull(repositories.offers.findById(SalesOfferId(12)))
            val restoredAuthorization =
                assertNotNull(
                    repositories.authorizations.findById(
                        SaleAuthorizationId("TRADE-301-PLAN-31-OFFER-12")
                    )
                )
            assertEquals("Main Store", restoredStore.name)
            assertEquals(1299, restoredOffer.price.fen)
            assertEquals("ONLINE", restoredOffer.channel.channelId)
            assertEquals(2, restoredAuthorization.quantity)
            assertEquals(301, restoredAuthorization.tradeId)
            assertEquals(31, restoredAuthorization.orderPlanId)
        }
    }

    @Test
    fun `offer lock exposes a committed suspension to the next transaction`() =
        database { factory ->
            transaction(factory) { entityManager ->
                val repositories = repositories(entityManager)
                repositories.stores.save(
                    Store(StoreId(11), MerchantId(21), "Main Store", StoreStatus.ACTIVE)
                )
                repositories.offers.save(offer(Instant.parse("2026-08-10T01:00:00Z")))
            }

            val firstLocked = CountDownLatch(1)
            val allowFirstCommit = CountDownLatch(1)
            val secondStarted = CountDownLatch(1)
            val pool = Executors.newFixedThreadPool(2)
            try {
                val first =
                    pool.submit<OfferStatus> {
                        transaction(factory) { entityManager ->
                            val offers = repositories(entityManager).offers
                            val locked = offers.lock(listOf(SalesOfferId(12))).single()
                            assertIs<Success<Unit>>(locked.suspend())
                            offers.save(locked)
                            entityManager.flush()
                            firstLocked.countDown()
                            assertTrue(allowFirstCommit.await(5, TimeUnit.SECONDS))
                            locked.status
                        }
                    }
                assertTrue(firstLocked.await(5, TimeUnit.SECONDS))
                val second =
                    pool.submit<OfferStatus> {
                        transaction(factory) { entityManager ->
                            secondStarted.countDown()
                            repositories(entityManager)
                                .offers
                                .lock(listOf(SalesOfferId(12)))
                                .single()
                                .status
                        }
                    }
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
                assertFailsWith<TimeoutException> { second.get(250, TimeUnit.MILLISECONDS) }
                allowFirstCommit.countDown()
                assertEquals(OfferStatus.SUSPENDED, first.get(5, TimeUnit.SECONDS))
                assertEquals(OfferStatus.SUSPENDED, second.get(5, TimeUnit.SECONDS))
            } finally {
                allowFirstCommit.countDown()
                pool.shutdownNow()
                assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
            }
        }

    private fun offer(now: Instant) =
        SalesOffer(
            SalesOfferId(12),
            StoreId(11),
            MerchantId(21),
            SkuId(22),
            Channel("ONLINE", "DEFAULT"),
            Price.ofFen(1299),
            OfferStatus.ACTIVE,
            EffectivePeriod(now.minusSeconds(60), now.plusSeconds(3600)),
            PurchaseLimit(5),
            FulfillmentPolicy(FulfillmentNodeId("DEFAULT"), false),
            1,
        )

    private data class Repositories(
        val stores: StoreRepositoryImpl,
        val offers: SalesOfferRepositoryImpl,
        val authorizations: SaleAuthorizationRepositoryImpl,
    )

    private fun repositories(entityManager: EntityManager): Repositories {
        val factory = JpaRepositoryFactory(entityManager)
        return Repositories(
            StoreRepositoryImpl(factory.getRepository(StorePOJpaRepository::class.java)),
            SalesOfferRepositoryImpl(factory.getRepository(SalesOfferPOJpaRepository::class.java)),
            SaleAuthorizationRepositoryImpl(
                factory.getRepository(SaleAuthorizationPOJpaRepository::class.java)
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
                    setPackagesToScan("com.jstore.shop.domain.offer.persistence")
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
