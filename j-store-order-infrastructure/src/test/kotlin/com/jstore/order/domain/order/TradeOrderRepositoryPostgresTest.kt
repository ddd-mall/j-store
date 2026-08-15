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
package com.jstore.order.domain.order

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class TradeOrderRepositoryPostgresTest {
    private val sequence = SnowFlakSequence(1, 1)

    @Test
    fun `trade source survives round trip and order plan is unique`() = database { factory ->
        transaction(factory) { entityManager ->
            val repository = repository(entityManager)
            repository.add(order(8001, "digest-a"))
            entityManager.flush()
            entityManager.clear()

            val restored = assertNotNull(repository.findBySourceOrderPlanId(8001))
            assertEquals(9001, restored.sourceTradeId)
            assertEquals("digest-a", restored.sourcePlanDigest)
            assertEquals(TradeStatus.ACTIVE, restored.tradeStatus)
            assertEquals(CommitmentStatus.CONFIRMED, restored.commitmentStatus)
        }

        assertFails {
            transaction(factory) { entityManager ->
                repository(entityManager).add(order(8001, "digest-b"))
                entityManager.flush()
            }
        }
    }

    private fun order(
        orderPlanId: Long,
        digest: String,
    ): Order {
        val factory = TrustedOrderFactoryImpl(sequence)
        return assertIs<Success<Order>>(
                factory.create(
                    TrustedOrderDraft(
                        tradeId = 9001,
                        orderPlanId = orderPlanId,
                        planDigest = digest,
                        merchantId = 7,
                        buyerId = 42,
                        buyerName = "张三",
                        buyerPhone = "+8613800138000",
                        recipientName = "张三",
                        recipientPhone = "+8613800138000",
                        recipientEmail = null,
                        shippingAddress = address(),
                        detailAddress = "示例路 1 号",
                        postalCode = null,
                        customsFields = emptyMap(),
                        items =
                            listOf(
                                TrustedOrderItemDraft(
                                    spuId = 201,
                                    skuId = 101,
                                    offerId = 11,
                                    storeId = 71,
                                    offerVersion = 1,
                                    fulfillmentNodeId = "NODE-1",
                                    channelId = "WEB",
                                    goodsName = "商品",
                                    skuDescription = "规格",
                                    quantity = 1,
                                    unitPrice = Price.ofFen(1000),
                                    catalogSnapshotVersion = 1,
                                )
                            ),
                        payableAmount = Price.ofFen(1000),
                        currency = "CNY",
                    )
                )
            )
            .value
    }

    private fun address() =
        I18nGeoAddress(
            CountryCode.CN,
            listOf(
                AddressComponent(
                    "110105",
                    DivisionLevel(3, "district"),
                    mapOf(Locale.CHINA to "朝阳区"),
                    Locale.CHINA,
                )
            ),
        )

    private fun repository(entityManager: EntityManager): OrderRepositoryImpl {
        val factory = JpaRepositoryFactory(entityManager)
        return OrderRepositoryImpl(
            factory.getRepository(OrderPOJpaRepository::class.java),
            entityManager,
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
                    setPackagesToScan("com.jstore.order.domain.order.persistence")
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
