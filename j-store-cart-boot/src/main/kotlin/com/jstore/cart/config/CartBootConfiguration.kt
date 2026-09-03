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
package com.jstore.cart.config

import com.jstore.cart.acl.CartCommerceFactsServiceImpl
import com.jstore.cart.api.CartCheckoutSourceQueryService
import com.jstore.cart.domain.CartAssessmentStore
import com.jstore.cart.domain.CartRepository
import com.jstore.cart.domain.CartRequestReceiptStore
import com.jstore.cart.service.*
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.api.CurrentGoodsSkuQueryService
import com.jstore.inventory.api.InventoryAvailabilityQueryService
import com.jstore.shop.api.OfferSnapshotQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class CartBootConfiguration {
    @Bean
    fun cartCommerceFactsService(
        goods: CurrentGoodsSkuQueryService,
        offers: OfferSnapshotQueryService,
        inventory: InventoryAvailabilityQueryService,
    ) = CartCommerceFactsServiceImpl(goods, offers, inventory)

    @Bean
    fun cartApplicationService(
        carts: CartRepository,
        assessments: CartAssessmentStore,
        receipts: CartRequestReceiptStore,
        commerce: CartCommerceFactsServiceImpl,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) =
        CartApplicationService(
            carts,
            assessments,
            receipts,
            commerce,
            CartIdentityGenerator(sequence::nextId),
            publisher,
        )

    @Bean
    fun cartRefreshRequestedHandler(
        service: CartApplicationService,
        carts: CartRepository,
    ) = CartRefreshRequestedHandler(service, carts)

    @Bean
    @Primary
    fun cartUseCase(
        service: CartApplicationService,
        tx: PlatformTransactionManager,
    ): CartUseCase =
        TransactionalCartUseCase(
            service,
            tx,
        )

    @Bean
    @Primary
    fun cartCheckoutSourceQueryService(
        service: CartApplicationService,
        tx: PlatformTransactionManager,
    ): CartCheckoutSourceQueryService =
        TransactionalCartCheckoutSourceQueryService(
            service,
            tx,
        )
}

class TransactionalCartUseCase(
    private val delegate: CartUseCase,
    manager: PlatformTransactionManager,
) : CartUseCase {
    private val write = TransactionTemplate(manager)
    private val read = TransactionTemplate(manager).apply { isReadOnly = true }

    override fun add(command: AddCartItemCommand) =
        requireNotNull(write.execute { delegate.add(command) })

    override fun replaceSelection(command: ReplaceCartSelectionCommand) =
        requireNotNull(write.execute { delegate.replaceSelection(command) })

    override fun refresh(buyerId: Long, requestId: String, expectedVersion: Long) =
        requireNotNull(write.execute { delegate.refresh(buyerId, requestId, expectedVersion) })

    override fun current(buyerId: Long) = requireNotNull(read.execute { delegate.current(buyerId) })
}

class TransactionalCartCheckoutSourceQueryService(
    private val delegate: CartCheckoutSourceQueryService,
    manager: PlatformTransactionManager,
) : CartCheckoutSourceQueryService {
    private val read = TransactionTemplate(manager).apply { isReadOnly = true }

    override fun prepare(query: com.jstore.cart.api.CartCheckoutSourceQuery) =
        requireNotNull(read.execute { delegate.prepare(query) })
}
