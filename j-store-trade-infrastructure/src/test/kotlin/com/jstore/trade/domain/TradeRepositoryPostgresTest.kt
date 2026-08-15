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

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Price
import com.jstore.trade.domain.persistence.TradePOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter

class TradeRepositoryPostgresTest {
    @Test
    fun `trade trusted snapshots and source plans survive PostgreSQL round trip`() =
        database { factory ->
            transaction(factory) { entityManager ->
                val repository = repository(entityManager)
                val trade = trade()
                val plan = trade.orderPlans.single()
                val expiresAt = Instant.parse("2030-01-01T00:00:00Z")
                trade.recordSaleAuthorized(
                    plan.id,
                    listOf(TradeAuthorization("A-1", plan.items.single().offerId, expiresAt)),
                )
                trade.recordInventoryReserved(plan.id, listOf("R-1"), expiresAt)
                trade.startOrderCreation()
                trade.recordOrderCreated(plan.id, 7001)
                trade.prepareSettlement(SettlementPlanId(9901))
                trade.recordPaymentPrepared(
                    SettlementPlanId(9901),
                    "FULL",
                    8001,
                    Price.ofFen(1000),
                    "CNY",
                )
                repository.save(trade)
                entityManager.flush()
                entityManager.clear()

                val restored = assertNotNull(repository.findById(TradeId(9001)))
                assertEquals("张三", restored.buyerProfile.displayName)
                assertEquals("110105", restored.recipient.shippingAddress.getLeafCode())
                assertEquals("商品", restored.orderPlans.single().items.single().goodsName)
                assertEquals(9101, restored.orderPlans.single().id.value)
                assertEquals(TradeStatus.PAYMENT_READY, restored.status)
                assertEquals(8001, restored.paymentIdFor("FULL"))
            }

            assertFails {
                transaction(factory) { entityManager ->
                    repository(entityManager).save(trade(9002, 9102))
                    entityManager.flush()
                }
            }
        }

    private fun trade(
        tradeId: Long = 9001,
        orderPlanId: Long = 9101,
    ) =
        Trade.start(
            TradeId(tradeId),
            "checkout-1",
            "v1:digest",
            BuyerPartySnapshot(PartyType.INDIVIDUAL, 42),
            TradeBuyerProfileSnapshot("张三", "+8613800138000"),
            42,
            TradeRecipientSnapshot(
                "张三",
                "CN",
                "+8613800138000",
                null,
                "110105",
                "示例路 1 号",
                address(),
            ),
            listOf(
                TradeOrderPlan(
                    TradeOrderPlanId(orderPlanId),
                    7,
                    "NODE-1",
                    listOf(
                        TradeItemSnapshot(
                            11,
                            71,
                            201,
                            101,
                            1,
                            1,
                            1,
                            "NODE-1",
                            "WEB",
                            Price.ofFen(1000),
                            "商品",
                            "规格",
                        )
                    ),
                    Price.ofFen(1000),
                )
            ),
            "CNY",
            CommitmentPolicySnapshot(TradeMode.NORMAL),
            SettlementTermsSnapshot(
                SettlementMode.PREPAID,
                FulfillmentReleaseRule.FULL_PAYMENT,
                listOf(
                    PaymentInstallmentSnapshot("FULL", InstallmentPurpose.FULL, Price.ofFen(1000))
                ),
            ),
        )

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

    private fun repository(entityManager: EntityManager): TradeRepositoryImpl {
        val factory = JpaRepositoryFactory(entityManager)
        return TradeRepositoryImpl(factory.getRepository(TradePOJpaRepository::class.java))
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
