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
package com.jstore.order.domain.aftersale

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.persistence.*
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.time.LocalDateTime
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.transaction.support.TransactionTemplate

class AfterSaleRepositoryPostgresTest {
    @Test
    fun `production repository allocates and persists one line of a multi-line order`() =
        database { fixture ->
            val aggregate = requested(1, listOf(11L))
            val result =
                fixture.createWithAllocation(
                    aggregate,
                    listOf(ceiling(11)),
                    receipt(aggregate, "one-line"),
                )
            assertIs<Success<AfterSale>>(result)
            assertEquals(aggregate.items, fixture.repository.findById(aggregate.id)?.items)
            assertEquals(1, fixture.capacities.findById(11).orElseThrow().requestedQuantity)
        }

    @Test
    fun `ceiling set mismatch and ceiling value mismatch leave no rows or receipt`() =
        database { fixture ->
            val aggregate = requested(2, listOf(21L, 22L))
            assertIs<Failure<*>>(
                fixture.createWithAllocation(
                    aggregate,
                    listOf(ceiling(21)),
                    receipt(aggregate, "missing"),
                )
            )
            val first = requested(3, listOf(31L))
            assertIs<Success<*>>(
                fixture.createWithAllocation(
                    first,
                    listOf(ceiling(31, quantity = 2)),
                    receipt(first, "first"),
                )
            )
            val changedSnapshot = requested(4, listOf(31L))
            assertIs<Failure<*>>(
                fixture.createWithAllocation(
                    changedSnapshot,
                    listOf(ceiling(31, quantity = 1)),
                    receipt(changedSnapshot, "changed"),
                )
            )
            assertTrue(fixture.roots.findById(2).isEmpty)
            assertTrue(
                fixture.receipts.findAll().none {
                    it.idempotencyKey == "missing" || it.idempotencyKey == "changed"
                }
            )
        }

    @Test
    fun `same row concurrent creation has one winner while different rows both succeed`() =
        database { fixture ->
            val pool = Executors.newFixedThreadPool(2)
            try {
                val same =
                    listOf(61L, 62L)
                        .map { id ->
                            pool.submit(
                                Callable {
                                    val a = requested(id, listOf(61L))
                                    fixture.createWithAllocation(
                                        a,
                                        listOf(ceiling(61)),
                                        receipt(a, "same-$id"),
                                    )
                                }
                            )
                        }
                        .map { it.get(15, TimeUnit.SECONDS) }
                assertEquals(1, same.count { it is Success })
                val different =
                    listOf(71L, 72L)
                        .map { item ->
                            pool.submit(
                                Callable {
                                    val a = requested(item, listOf(item))
                                    fixture.createWithAllocation(
                                        a,
                                        listOf(ceiling(item)),
                                        receipt(a, "different-$item"),
                                    )
                                }
                            )
                        }
                        .map { it.get(15, TimeUnit.SECONDS) }
                assertTrue(different.all { it is Success })
            } finally {
                pool.shutdownNow()
            }
        }

    private fun database(test: (Fixture) -> Unit) {
        EmbeddedPostgres.builder().start().use { pg ->
            val bean =
                LocalContainerEntityManagerFactoryBean().apply {
                    dataSource = pg.postgresDatabase
                    jpaVendorAdapter = HibernateJpaVendorAdapter()
                    setPackagesToScan("com.jstore.order.domain.aftersale.persistence")
                    setJpaPropertyMap(
                        mapOf(
                            "hibernate.hbm2ddl.auto" to "create-drop",
                            "hibernate.dialect" to "org.hibernate.dialect.PostgreSQLDialect",
                        )
                    )
                    afterPropertiesSet()
                }
            val emf = bean.`object`!!
            try {
                val em = SharedEntityManagerCreator.createSharedEntityManager(emf)
                val factory = JpaRepositoryFactory(em)
                val roots = factory.getRepository(AfterSalePOJpaRepository::class.java)
                val capacities = factory.getRepository(AfterSaleCapacityPOJpaRepository::class.java)
                val receipts =
                    factory.getRepository(AfterSaleCommandReceiptPOJpaRepository::class.java)
                val transactions = TransactionTemplate(JpaTransactionManager(emf))
                test(
                    Fixture(
                        AfterSaleRepositoryImpl(
                            roots,
                            capacities,
                            receipts,
                            SnowFlakSequence(1, 1),
                        ),
                        roots,
                        capacities,
                        receipts,
                        transactions,
                    )
                )
            } finally {
                emf.close()
            }
        }
    }

    private data class Fixture(
        val repository: AfterSaleRepositoryImpl,
        val roots: AfterSalePOJpaRepository,
        val capacities: AfterSaleCapacityPOJpaRepository,
        val receipts: AfterSaleCommandReceiptPOJpaRepository,
        val transactions: TransactionTemplate,
    ) {
        fun createWithAllocation(
            afterSale: AfterSale,
            ceilings: List<RefundCapacityCeiling>,
            receipt: AfterSaleCommandReceipt,
        ) =
            requireNotNull(
                transactions.execute {
                    repository.createWithAllocation(afterSale, ceilings, receipt)
                }
            )
    }

    private fun requested(id: Long, itemIds: List<Long>): AfterSaleImpl {
        val now = LocalDateTime.of(2026, 8, 3, 10, 0)
        val items = itemIds.map { itemId ->
            AfterSaleItemImpl(
                AfterSaleItemId(id * 100 + itemId),
                OrderId(9),
                OrderItemId(itemId),
                1,
                Price.ofFen(100),
                "CNY",
                RefundEligibilitySnapshot(
                    OrderItemId(itemId),
                    1,
                    Price.ofFen(100),
                    "CNY",
                    GoodsSnapshot(itemId, itemId, "goods", "sku"),
                ),
            )
        }
        return AfterSaleImpl(
            AfterSaleId(id),
            OrderId(9),
            ApplicantActorId(1),
            MerchantActorId(2),
            AfterSaleStatus.REQUESTED,
            RefundReason(RefundCategory.OTHER, "reason"),
            FulfillmentSnapshot(FulfillmentStatus.UNFULFILLED, false),
            items,
            createTime = now,
            _updateTime = now,
        )
    }

    private fun ceiling(itemId: Long, quantity: Int = 1) =
        RefundCapacityCeiling(OrderId(9), OrderItemId(itemId), quantity, Price.ofFen(100))

    private fun receipt(a: AfterSale, key: String) =
        AfterSaleCommandReceipt(
            1,
            AfterSaleCommandType.CREATE,
            key,
            key,
            a.id,
            a.status,
            LocalDateTime.of(2026, 8, 3, 10, 0),
        )
}
