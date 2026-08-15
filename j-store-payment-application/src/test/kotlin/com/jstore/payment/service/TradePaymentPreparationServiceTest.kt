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
package com.jstore.payment.service

import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.*
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.payment.domain.payment.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradePaymentPreparationServiceTest {
    private val instant = Instant.parse("2029-01-01T00:00:00Z")

    @Test
    fun `accepted provider result persists ready payment and publishes public fact`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        val provider = PaymentProviderGateway { request ->
            assertEquals("200:FULL", request.idempotencyKey)
            PaymentProviderResult.Accepted(
                "provider-1",
                "opaque-payment-action",
                instant,
                instant.plusSeconds(900),
            )
        }
        val service = service(repository, provider, publisher)

        assertEquals(true, assertIs<Success<Boolean>>(service.prepare(command())).value)

        val payment = requireNotNull(repository.payment)
        assertEquals(TradePaymentStatus.READY, payment.status)
        val event = assertIs<PaymentPreparedIntegrationEvent>(publisher.messages.single())
        assertEquals(payment.id.value, event.paymentId)
    }

    @Test
    fun `provider exception is uncertain and never converted to rejection`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        val service =
            service(
                repository,
                PaymentProviderGateway { error("network timeout") },
                publisher,
            )

        assertIs<Success<Boolean>>(service.prepare(command()))

        assertEquals(TradePaymentStatus.UNCERTAIN, repository.payment?.status)
        assertIs<PaymentPreparationUncertainIntegrationEvent>(publisher.messages.single())
    }

    @Test
    fun `explicit provider rejection publishes rejected fact`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        val service =
            service(
                repository,
                PaymentProviderGateway { PaymentProviderResult.Rejected("declined") },
                publisher,
            )

        assertIs<Success<Boolean>>(service.prepare(command()))

        assertEquals(TradePaymentStatus.REJECTED, repository.payment?.status)
        assertIs<PaymentPreparationRejectedIntegrationEvent>(publisher.messages.single())
    }

    @Test
    fun `duplicate preparation reuses payment without invoking provider twice`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        var providerCalls = 0
        val service =
            service(
                repository,
                PaymentProviderGateway {
                    providerCalls++
                    PaymentProviderResult.Accepted(
                        "provider-1",
                        "opaque-payment-action",
                        instant,
                        instant.plusSeconds(300),
                    )
                },
                publisher,
            )

        assertEquals(true, assertIs<Success<Boolean>>(service.prepare(command())).value)
        assertEquals(false, assertIs<Success<Boolean>>(service.prepare(command())).value)

        assertEquals(1, providerCalls)
        assertEquals(1, repository.generatedPaymentCount)
        assertEquals(1, publisher.messages.map { it.messageId }.distinct().size)
    }

    @Test
    fun `retry after preparation persistence reuses payment identity before provider result`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        var generatedId = 8000L
        val service =
            TradePaymentPreparationService(
                repository,
                { ++generatedId },
                PaymentProviderGateway { error("provider must not be invoked by start") },
                publisher,
            ) {
                instant
            }

        val first =
            assertIs<TradePaymentPreparationStart.Pending>(
                assertIs<Success<TradePaymentPreparationStart>>(service.start(command())).value
            )
        val retry =
            assertIs<TradePaymentPreparationStart.Pending>(
                assertIs<Success<TradePaymentPreparationStart>>(service.start(command())).value
            )

        assertEquals(first.request.paymentId, retry.request.paymentId)
        assertEquals(8001, retry.request.paymentId)
        assertEquals(1, repository.generatedPaymentCount)
        assertEquals(emptyList(), publisher.messages)
    }

    @Test
    fun `ready payment is cancelled only after provider confirmation`() {
        val repository = FakeTradePaymentRepository()
        val ready =
            preparedPayment().also {
                it.markReady(
                    "provider-1",
                    "opaque-payment-action",
                    instant,
                    instant.plusSeconds(600),
                    instant.plusSeconds(300),
                )
            }
        repository.save(ready)
        val publisher = CapturingPublisher()
        var providerCalls = 0
        val service =
            TradePaymentCancellationService(
                repository,
                PaymentProviderCancellationGateway {
                    providerCalls++
                    PaymentProviderCancellationResult.Confirmed
                },
                publisher,
            ) {
                instant
            }

        assertEquals(true, assertIs<Success<Boolean>>(service.cancel(cancelCommand())).value)

        assertEquals(1, providerCalls)
        assertEquals(TradePaymentStatus.CANCELLED, repository.payment?.status)
        assertIs<PaymentCancellationConfirmedIntegrationEvent>(publisher.messages.single())
    }

    @Test
    fun `preparing payment waits for its provider result before cancellation`() {
        val repository = FakeTradePaymentRepository()
        repository.save(preparedPayment())
        val publisher = CapturingPublisher()
        var providerCalls = 0
        val service =
            TradePaymentCancellationService(
                repository,
                PaymentProviderCancellationGateway {
                    providerCalls++
                    PaymentProviderCancellationResult.Confirmed
                },
                publisher,
            ) {
                instant
            }

        assertEquals(true, assertIs<Success<Boolean>>(service.cancel(cancelCommand())).value)

        assertEquals(0, providerCalls)
        assertEquals(TradePaymentStatus.PREPARATION_CANCELLING, repository.payment?.status)
        assertEquals(emptyList(), publisher.messages)
    }

    @Test
    fun `uncertain provider cancellation remains durable and retryable`() {
        val repository = FakeTradePaymentRepository()
        repository.save(preparedPayment().also { it.markUncertain("preparation timeout") })
        var providerCalls = 0
        val service =
            TradePaymentCancellationService(
                repository,
                PaymentProviderCancellationGateway {
                    providerCalls++
                    PaymentProviderCancellationResult.Uncertain("timeout")
                },
                CapturingPublisher(),
            ) {
                instant
            }

        assertIs<com.jstore.common.utils.Failure<*>>(service.cancel(cancelCommand()))
        assertIs<com.jstore.common.utils.Failure<*>>(service.cancel(cancelCommand()))

        assertEquals(TradePaymentStatus.CANCELLING, repository.payment?.status)
        assertEquals("cancelled", repository.payment?.cancellationReason)
        assertEquals("timeout", repository.payment?.failureReason)
        assertEquals(2, providerCalls)
    }

    @Test
    fun `confirmed retry publishes the original cancellation reason`() {
        val repository = FakeTradePaymentRepository()
        repository.save(preparedPayment().also { it.markUncertain("preparation timeout") })
        val publisher = CapturingPublisher()
        var providerCalls = 0
        val service =
            TradePaymentCancellationService(
                repository,
                PaymentProviderCancellationGateway {
                    providerCalls++
                    if (providerCalls == 1) {
                        PaymentProviderCancellationResult.Uncertain("provider timeout")
                    } else {
                        PaymentProviderCancellationResult.Confirmed
                    }
                },
                publisher,
            ) {
                instant
            }

        assertIs<com.jstore.common.utils.Failure<*>>(service.cancel(cancelCommand()))
        assertIs<Success<Boolean>>(service.cancel(cancelCommand()))

        val event =
            assertIs<PaymentCancellationConfirmedIntegrationEvent>(publisher.messages.single())
        assertEquals("cancelled", event.reason)
        assertEquals("provider timeout", repository.payment?.failureReason)
    }

    @Test
    fun `retry prepare resolves pending cancellation without exposing a payment`() {
        val repository = FakeTradePaymentRepository()
        val payment = preparedPayment()
        payment.requestCancellation("buyer cancelled")
        repository.save(payment)
        val publisher = CapturingPublisher()
        var providerCalls = 0
        val service =
            service(
                repository,
                PaymentProviderGateway {
                    providerCalls++
                    PaymentProviderResult.Rejected("not accepted")
                },
                publisher,
            )

        assertEquals(true, assertIs<Success<Boolean>>(service.prepare(command())).value)

        assertEquals(1, providerCalls)
        assertEquals(TradePaymentStatus.CANCELLED, repository.payment?.status)
        assertIs<PaymentCancellationConfirmedIntegrationEvent>(publisher.messages.single())
    }

    @Test
    fun `provider acceptance racing cancellation requires a fresh provider cancellation`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        val preparation =
            service(
                repository,
                PaymentProviderGateway { error("provider is invoked explicitly in this test") },
                publisher,
            )
        val preparationStart =
            assertIs<TradePaymentPreparationStart.Pending>(
                assertIs<Success<TradePaymentPreparationStart>>(preparation.start(command())).value
            )
        var cancellationCalls = 0
        val cancellation =
            TradePaymentCancellationService(
                repository,
                PaymentProviderCancellationGateway {
                    cancellationCalls++
                    PaymentProviderCancellationResult.Confirmed
                },
                publisher,
            ) {
                instant
            }
        assertEquals(true, assertIs<Success<Boolean>>(cancellation.cancel(cancelCommand())).value)
        val accepted =
            PaymentProviderResult.Accepted(
                "provider-1",
                "opaque-payment-action",
                instant,
                instant.plusSeconds(300),
            )

        assertEquals(
            true,
            assertIs<Success<Boolean>>(
                    preparation.complete(command(), preparationStart.request.paymentId, accepted)
                )
                .value,
        )

        assertEquals(TradePaymentStatus.CANCELLING, repository.payment?.status)
        assertEquals("provider-1", repository.payment?.providerReference)
        val retry = assertIs<CancelPaymentInstallmentCommand>(publisher.messages.single())

        assertEquals(true, assertIs<Success<Boolean>>(cancellation.cancel(retry)).value)
        assertEquals(1, cancellationCalls)
        assertEquals(TradePaymentStatus.CANCELLED, repository.payment?.status)
        assertIs<PaymentCancellationConfirmedIntegrationEvent>(publisher.messages.last())
    }

    @Test
    fun `invalid accepted result racing cancellation remains cancellable`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        val preparation =
            service(
                repository,
                PaymentProviderGateway { error("provider is invoked explicitly in this test") },
                publisher,
            )
        val start =
            assertIs<TradePaymentPreparationStart.Pending>(
                assertIs<Success<TradePaymentPreparationStart>>(preparation.start(command())).value
            )
        val cancellation =
            TradePaymentCancellationService(
                repository,
                PaymentProviderCancellationGateway {
                    PaymentProviderCancellationResult.Confirmed
                },
                publisher,
            ) {
                instant
            }
        assertIs<Success<Boolean>>(cancellation.cancel(cancelCommand()))
        val oversizedReference =
            "provider-" + "r".repeat(TradePayment.MAX_PROVIDER_REFERENCE_LENGTH)

        val completed =
            preparation.complete(
                command(),
                start.request.paymentId,
                PaymentProviderResult.Accepted(
                    oversizedReference,
                    "a".repeat(TradePayment.MAX_PAY_ACTION_LENGTH + 1),
                    instant,
                    instant.plusSeconds(1800),
                ),
            )

        assertEquals(true, assertIs<Success<Boolean>>(completed).value)
        assertEquals(TradePaymentStatus.CANCELLING, repository.payment?.status)
        assertEquals(oversizedReference, repository.payment?.providerReference)
        assertEquals(null, repository.payment?.payAction)
        assertEquals("cancelled", repository.payment?.cancellationReason)
        assertIs<CancelPaymentInstallmentCommand>(publisher.messages.single())
    }

    @Test
    fun `oversized provider result becomes uncertain instead of failing persistence later`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        val service =
            service(
                repository,
                PaymentProviderGateway {
                    PaymentProviderResult.Accepted(
                        "r".repeat(257),
                        "opaque-payment-action",
                        instant,
                        instant.plusSeconds(300),
                    )
                },
                publisher,
            )

        assertIs<Success<Boolean>>(service.prepare(command()))

        assertEquals(TradePaymentStatus.UNCERTAIN, repository.payment?.status)
        assertIs<PaymentPreparationUncertainIntegrationEvent>(publisher.messages.single())
    }

    @Test
    fun `oversized provider rejection reason becomes bounded uncertain result`() {
        val repository = FakeTradePaymentRepository()
        val publisher = CapturingPublisher()
        val service =
            service(
                repository,
                PaymentProviderGateway { PaymentProviderResult.Rejected("r".repeat(1025)) },
                publisher,
            )

        assertIs<Success<Boolean>>(service.prepare(command()))

        assertEquals(TradePaymentStatus.UNCERTAIN, repository.payment?.status)
        assertEquals(
            "provider returned an invalid rejection reason",
            repository.payment?.failureReason,
        )
        assertIs<PaymentPreparationUncertainIntegrationEvent>(publisher.messages.single())
    }

    private fun service(
        repository: FakeTradePaymentRepository,
        provider: PaymentProviderGateway,
        publisher: CapturingPublisher,
    ) = TradePaymentPreparationService(repository, { 8001 }, provider, publisher) { instant }

    private fun command() =
        PreparePaymentInstallmentCommand(
            tradeId = 100,
            settlementPlanId = 200,
            installmentId = "FULL",
            amountFen = 1000,
            currency = "CNY",
            allocations = listOf(ContractPaymentAllocation(11, 21, 7, 1000)),
            sourceMessageId = "source-1",
            occurredAtValue = instant,
            acceptBefore = instant.plusSeconds(600),
            expiresAt = instant.plusSeconds(900),
        )

    private fun cancelCommand() =
        CancelPaymentInstallmentCommand(100, 200, "FULL", "cancelled", "source-2", instant)

    private fun preparedPayment(id: Long = 8001) =
        TradePayment.prepare(
            TradePaymentId(id),
            100,
            200,
            "FULL",
            com.jstore.common.properties.Price.ofFen(1000),
            "CNY",
            listOf(
                PaymentAllocationSnapshot(
                    11,
                    21,
                    7,
                    com.jstore.common.properties.Price.ofFen(1000),
                )
            ),
            instant,
        )
}

private class FakeTradePaymentRepository : TradePaymentRepository {
    var payment: TradePayment? = null
    var generatedPaymentCount = 0

    override fun save(aggregate: TradePayment): TradePayment = aggregate.also {
        if (payment == null) generatedPaymentCount++
        payment = it
    }

    override fun findById(id: TradePaymentId): TradePayment? = payment?.takeIf { it.id == id }

    override fun findByInstallment(
        settlementPlanId: Long,
        installmentId: String,
    ): TradePayment? = payment?.takeIf {
        it.settlementPlanId == settlementPlanId && it.installmentId == installmentId
    }
}

private class CapturingPublisher : IntegrationMessagePublisher {
    val messages = mutableListOf<IntegrationMessage>()

    override fun publish(message: IntegrationMessage) {
        messages += message
    }
}
