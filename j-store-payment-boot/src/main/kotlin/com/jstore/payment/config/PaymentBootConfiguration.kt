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

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.payment.domain.payment.PaymentOrderRepository
import com.jstore.payment.domain.payment.TradePaymentRepository
import com.jstore.payment.service.CancelPaymentInstallmentCommandHandler
import com.jstore.payment.service.PaymentApplicationService
import com.jstore.payment.service.PaymentProviderCancellationGateway
import com.jstore.payment.service.PaymentProviderCancellationResult
import com.jstore.payment.service.PaymentProviderGateway
import com.jstore.payment.service.PaymentProviderRequest
import com.jstore.payment.service.PaymentProviderResult
import com.jstore.payment.service.PaymentUseCase
import com.jstore.payment.service.PreparePaymentInstallmentCommandHandler
import com.jstore.payment.service.RequestPaymentRefundCommandHandler
import com.jstore.payment.service.TradePaymentCancellationService
import com.jstore.payment.service.TradePaymentCancellationUseCase
import com.jstore.payment.service.TradePaymentPreparationService
import com.jstore.payment.service.TradePaymentPreparationUseCase
import java.time.Instant
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class PaymentBootConfiguration {
    @Bean
    fun checkoutPaymentGateway(payments: TradePaymentRepository) = CheckoutPaymentAdapter(payments)

    @Bean
    fun tradePaymentPreparationUseCase(
        payments: TradePaymentRepository,
        sequence: SnowFlakSequence,
        provider: PaymentProviderGateway,
        publisher: IntegrationMessagePublisher,
        transactionManager: PlatformTransactionManager,
    ): TradePaymentPreparationUseCase {
        val delegate =
            TradePaymentPreparationService(payments, { sequence.nextId() }, provider, publisher)
        return TransactionalTradePaymentPreparationUseCase(
            delegate,
            SpringTradePaymentPreparationTransactionOperations(transactionManager),
        )
    }

    /** Internal-development provider. Replace this bean before enabling a production profile. */
    @Bean
    @Profile("!production")
    fun localPaymentProviderGateway(): PaymentProviderGateway =
        PaymentProviderGateway { request: PaymentProviderRequest ->
            val acceptedAt = Instant.now()
            PaymentProviderResult.Accepted(
                providerReference = "local:${request.idempotencyKey}",
                payAction = "jstore://payments/${request.paymentId}",
                acceptedAt = acceptedAt,
                expiresAt = request.expiresAt,
            )
        }

    @Bean
    @Profile("!production")
    fun localPaymentProviderCancellationGateway(): PaymentProviderCancellationGateway =
        PaymentProviderCancellationGateway {
            PaymentProviderCancellationResult.Confirmed
        }

    @Bean
    fun preparePaymentInstallmentCommandHandler(
        service: TradePaymentPreparationUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<com.jstore.contracts.commerce.PreparePaymentInstallmentCommand> =
        transactional(PreparePaymentInstallmentCommandHandler(service), transactionManager)

    @Bean
    fun tradePaymentCancellationUseCase(
        payments: TradePaymentRepository,
        provider: PaymentProviderCancellationGateway,
        publisher: IntegrationMessagePublisher,
        transactionManager: PlatformTransactionManager,
    ): TradePaymentCancellationUseCase {
        val delegate = TradePaymentCancellationService(payments, provider, publisher)
        return TransactionalTradePaymentCancellationUseCase(
            delegate,
            SpringTradePaymentPreparationTransactionOperations(transactionManager),
        )
    }

    @Bean
    fun cancelPaymentInstallmentCommandHandler(
        service: TradePaymentCancellationUseCase,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<com.jstore.contracts.commerce.CancelPaymentInstallmentCommand> =
        transactional(CancelPaymentInstallmentCommandHandler(service), transactionManager)

    @Bean
    fun paymentApplicationService(
        repository: PaymentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = PaymentApplicationService(repository, sequence, publisher)

    @Bean
    @Primary
    fun transactionalPaymentUseCase(
        paymentApplicationService: PaymentApplicationService,
        transactionManager: PlatformTransactionManager,
    ): PaymentUseCase = TransactionalPaymentUseCase(paymentApplicationService, transactionManager)

    @Bean
    fun requestPaymentRefundCommandHandler(service: PaymentUseCase) =
        RequestPaymentRefundCommandHandler(service)

    private fun <T : IntegrationMessage> transactional(
        delegate: IntegrationMessageHandler<T>,
        transactionManager: PlatformTransactionManager,
    ): IntegrationMessageHandler<T> =
        object : IntegrationMessageHandler<T> {
            private val transaction = TransactionTemplate(transactionManager)

            override fun handlerId() = delegate.handlerId()

            override fun handle(message: T) {
                transaction.executeWithoutResult { delegate.handle(message) }
            }
        }
}
