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
import com.jstore.payment.domain.payment.PaymentOrderRepository
import com.jstore.payment.domain.payment.TradePaymentRepository
import com.jstore.payment.service.PaymentApplicationService
import com.jstore.payment.service.PaymentUseCase
import com.jstore.payment.service.RequestPaymentRefundCommandHandler
import com.jstore.trade.service.TradeSettlementGateway
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class PaymentBootConfiguration {
    @Bean
    fun tradeSettlementGateway(
        payments: TradePaymentRepository,
        sequence: SnowFlakSequence,
    ): TradeSettlementGateway = TradeSettlementAdapter(payments, sequence)

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
}
