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
package com.jstore.cart.domain

import com.jstore.cart.domain.persistence.*
import com.jstore.common.properties.Price
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.time.Instant
import java.util.concurrent.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@SpringBootTest(classes = [CartAssessmentConcurrencyTest.Config::class])
class CartAssessmentConcurrencyTest {
    @Autowired lateinit var store: CartAssessmentStore
    @Autowired lateinit var manager: PlatformTransactionManager
    @Autowired lateinit var jpa: CartAssessmentPOJpaRepository

    @org.junit.jupiter.api.BeforeEach
    fun clean() {
        jpa.deleteAll()
    }

    @Test
    fun `concurrent writers return the same persisted assessment`() {
        val ready = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results =
                (1L..2L)
                    .map { id ->
                        pool.submit<CartAssessment> {
                            TransactionTemplate(manager).execute {
                                assertNull(store.findByCartAndVersion(CartId(99), 1))
                                ready.await(10, TimeUnit.SECONDS)
                                store.save(candidate(id, 99))
                            }!!
                        }
                    }
                    .map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(results[0].id, results[1].id)
            assertEquals(results[0].lines, results[1].lines)
            assertEquals(results[0].estimatedAmount, results[1].estimatedAmount)
            assertEquals(1, results[0].lines.size)
            assertEquals(1, jpa.count())
        } finally {
            pool.shutdownNow()
        }
    }

    private fun candidate(id: Long, cartId: Long) =
        CartAssessment(
            CartAssessmentId(id),
            CartId(cartId),
            1,
            AssessmentStatus.COMPLETE,
            Price.ofFen(id * 100),
            "CNY",
            Instant.now(),
            listOf(
                CartAssessmentLine(
                    CartLineId(id),
                    LineAssessmentStatus.ELIGIBLE,
                    Price.ofFen(id * 100),
                    id,
                    id,
                    5,
                    Price.ofFen(id * 100),
                )
            ),
        )

    @Test
    fun `rolled back winner leaves no partial header or lines and another writer succeeds`() {
        val inserted = CountDownLatch(1)
        val attempted = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val winner = pool.submit {
                TransactionTemplate(manager).executeWithoutResult { tx ->
                    store.save(candidate(11, 199))
                    inserted.countDown()
                    check(attempted.await(10, TimeUnit.SECONDS))
                    tx.setRollbackOnly()
                }
            }
            val contender =
                pool.submit<CartAssessment> {
                    check(inserted.await(10, TimeUnit.SECONDS))
                    TransactionTemplate(manager).execute {
                        attempted.countDown()
                        store.save(candidate(12, 199))
                    }!!
                }
            winner.get(20, TimeUnit.SECONDS)
            val result = contender.get(20, TimeUnit.SECONDS)
            assertEquals(CartAssessmentId(12), result.id)
            assertEquals(candidate(12, 199).lines, result.lines)
            assertFalse(jpa.existsById(11))
        } finally {
            pool.shutdownNow()
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = [CartAssessmentPO::class])
    @EnableJpaRepositories(basePackageClasses = [CartAssessmentPOJpaRepository::class])
    @Import(CartAssessmentStoreImpl::class)
    class Config

    companion object {
        private val postgres by lazy { EmbeddedPostgres.builder().start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.getJdbcUrl("postgres", "postgres") }
            registry.add("spring.datasource.username") { "postgres" }
            registry.add("spring.datasource.password") { "" }
            registry.add("spring.jpa.hibernate.ddl-auto") { "create-drop" }
            registry.add("spring.flyway.enabled") { "false" }
        }
    }
}
