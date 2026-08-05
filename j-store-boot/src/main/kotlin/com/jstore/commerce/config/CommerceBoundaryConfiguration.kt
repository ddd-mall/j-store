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
package com.jstore.commerce.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.fulfillment.domain.FulfillmentOrderRepository
import com.jstore.fulfillment.service.CreateFulfillmentForOrderCommandHandler
import com.jstore.fulfillment.service.FulfillmentApplicationService
import com.jstore.payment.domain.payment.PaymentOrderRepository
import com.jstore.payment.service.CreatePaymentForOrderCommandHandler
import com.jstore.payment.service.PaymentApplicationService
import com.jstore.payment.service.RequestPaymentRefundCommandHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CommerceBoundaryConfiguration {
    @Bean
    fun paymentApplicationService(
        repository: PaymentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = PaymentApplicationService(repository, sequence, publisher)

    @Bean
    fun fulfillmentApplicationService(
        repository: FulfillmentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = FulfillmentApplicationService(repository, sequence, publisher)

    @Bean
    fun createPaymentForOrderCommandHandler(service: PaymentApplicationService) =
        CreatePaymentForOrderCommandHandler(service)

    @Bean
    fun requestPaymentRefundCommandHandler(service: PaymentApplicationService) =
        RequestPaymentRefundCommandHandler(service)

    @Bean
    fun createFulfillmentForOrderCommandHandler(service: FulfillmentApplicationService) =
        CreateFulfillmentForOrderCommandHandler(service)
}
