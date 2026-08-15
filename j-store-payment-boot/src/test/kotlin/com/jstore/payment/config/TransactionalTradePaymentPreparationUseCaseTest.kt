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
package com.jstore.payment.config

import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.CancelPaymentInstallmentCommand
import com.jstore.contracts.commerce.ContractPaymentAllocation
import com.jstore.contracts.commerce.PreparePaymentInstallmentCommand
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.payment.domain.payment.TradePayment
import com.jstore.payment.domain.payment.TradePaymentId
import com.jstore.payment.domain.payment.TradePaymentRepository
import com.jstore.payment.service.PaymentProviderCancellationGateway
import com.jstore.payment.service.PaymentProviderCancellationResult
import com.jstore.payment.service.PaymentProviderGateway
import com.jstore.payment.service.PaymentProviderResult
import com.jstore.payment.service.TradePaymentCancellationService
import com.jstore.payment.service.TradePaymentPreparationService
import java.sql.Connection
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalTradePaymentPreparationUseCaseTest {
    @Test
    fun `spring transaction operations suspend ambient transaction around provider call`() {
        val outerConnection = mock<Connection>()
        val durableConnection = mock<Connection>()
        whenever(outerConnection.autoCommit).thenReturn(true)
        whenever(durableConnection.autoCommit).thenReturn(true)
        val dataSource = mock<DataSource>()
        whenever(dataSource.connection).thenReturn(outerConnection, durableConnection)
        val transactionManager = DataSourceTransactionManager(dataSource)
        val operations = SpringTradePaymentPreparationTransactionOperations(transactionManager)

        TransactionTemplate(transactionManager).executeWithoutResult {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
            operations.durable {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
            }
            operations.withoutTransaction {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive())
            }
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
        }
    }

    @Test
    fun `commits stable preparation before invoking provider outside transaction`() {
        val instant = Instant.parse("2030-01-01T00:00:00Z")
        val transactions = RecordingPaymentTransactions()
        val repository = InMemoryTradePaymentRepository()
        val service =
            TradePaymentPreparationService(
                repository,
                { 8001 },
                PaymentProviderGateway {
                    assertFalse(transactions.inDurableTransaction)
                    PaymentProviderResult.Accepted(
                        "provider-1",
                        "opaque-action",
                        instant,
                        instant.plusSeconds(300),
                    )
                },
                object : IntegrationMessagePublisher {
                    override fun publish(message: IntegrationMessage) = Unit
                },
            ) {
                instant
            }
        val useCase = TransactionalTradePaymentPreparationUseCase(service, transactions)

        assertIs<Success<Boolean>>(useCase.prepare(command(instant)))

        assertEquals(listOf("durable", "external", "durable"), transactions.phases)
        assertEquals(8001, repository.payment?.id?.value)
    }

    @Test
    fun `commits cancelling state before invoking provider outside transaction`() {
        val instant = Instant.parse("2030-01-01T00:00:00Z")
        val transactions = RecordingPaymentTransactions()
        val repository = InMemoryTradePaymentRepository()
        repository.save(
            TradePayment.prepare(
                    TradePaymentId(8001),
                    100,
                    200,
                    "FULL",
                    com.jstore.common.properties.Price.ofFen(1000),
                    "CNY",
                    listOf(
                        com.jstore.payment.domain.payment.PaymentAllocationSnapshot(
                            11,
                            21,
                            7,
                            com.jstore.common.properties.Price.ofFen(1000),
                        )
                    ),
                    instant,
                )
                .also {
                    it.markReady(
                        "provider-1",
                        "opaque-action",
                        instant,
                        instant.plusSeconds(60),
                        instant.plusSeconds(600),
                    )
                }
        )
        val service =
            TradePaymentCancellationService(
                repository,
                PaymentProviderCancellationGateway {
                    assertFalse(transactions.inDurableTransaction)
                    PaymentProviderCancellationResult.Confirmed
                },
                object : IntegrationMessagePublisher {
                    override fun publish(message: IntegrationMessage) = Unit
                },
            ) {
                instant
            }

        assertIs<Success<Boolean>>(
            TransactionalTradePaymentCancellationUseCase(service, transactions)
                .cancel(
                    CancelPaymentInstallmentCommand(100, 200, "FULL", "cancel", "source", instant)
                )
        )

        assertEquals(listOf("durable", "external", "durable"), transactions.phases)
        assertEquals(
            com.jstore.payment.domain.payment.TradePaymentStatus.CANCELLED,
            repository.payment?.status,
        )
    }

    private fun command(instant: Instant) =
        PreparePaymentInstallmentCommand(
            100,
            200,
            "FULL",
            1000,
            "CNY",
            listOf(ContractPaymentAllocation(11, 21, 7, 1000)),
            "source-1",
            instant,
            instant.plusSeconds(60),
            instant.plusSeconds(600),
        )
}

private class RecordingPaymentTransactions : TradePaymentPreparationTransactionOperations {
    val phases = mutableListOf<String>()
    var inDurableTransaction = false
        private set

    override fun <T : Any> durable(action: () -> T): T {
        phases += "durable"
        check(!inDurableTransaction)
        inDurableTransaction = true
        return try {
            action()
        } finally {
            inDurableTransaction = false
        }
    }

    override fun <T : Any> withoutTransaction(action: () -> T): T {
        phases += "external"
        check(!inDurableTransaction)
        return action()
    }
}

private class InMemoryTradePaymentRepository : TradePaymentRepository {
    var payment: TradePayment? = null

    override fun save(aggregate: TradePayment): TradePayment = aggregate.also { payment = it }

    override fun findById(id: TradePaymentId): TradePayment? = payment?.takeIf { it.id == id }

    override fun findByInstallment(
        settlementPlanId: Long,
        installmentId: String,
    ): TradePayment? = payment?.takeIf {
        it.settlementPlanId == settlementPlanId && it.installmentId == installmentId
    }
}
