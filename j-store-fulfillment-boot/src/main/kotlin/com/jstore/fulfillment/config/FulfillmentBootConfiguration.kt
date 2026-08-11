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
package com.jstore.fulfillment.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.fulfillment.domain.FulfillmentOrderRepository
import com.jstore.fulfillment.service.CreateFulfillmentForOrderCommandHandler
import com.jstore.fulfillment.service.FulfillmentApplicationService
import com.jstore.fulfillment.service.FulfillmentUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class FulfillmentBootConfiguration {
    @Bean
    fun fulfillmentApplicationService(
        repository: FulfillmentOrderRepository,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) = FulfillmentApplicationService(repository, sequence, publisher)

    @Bean
    @Primary
    fun transactionalFulfillmentUseCase(
        fulfillmentApplicationService: FulfillmentApplicationService,
        transactionManager: PlatformTransactionManager,
    ): FulfillmentUseCase =
        TransactionalFulfillmentUseCase(fulfillmentApplicationService, transactionManager)

    @Bean
    fun createFulfillmentForOrderCommandHandler(service: FulfillmentUseCase) =
        CreateFulfillmentForOrderCommandHandler(service)
}
