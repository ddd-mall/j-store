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
import com.jstore.cart.service.*
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.api.CurrentGoodsSkuQueryService
import com.jstore.inventory.api.InventoryAvailabilityQueryService
import com.jstore.shop.api.OfferSnapshotQueryService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
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
        commerce: CartCommerceFactsServiceImpl,
        sequence: SnowFlakSequence,
        publisher: DomainEventPublisher,
    ) =
        CartApplicationService(
            carts,
            assessments,
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
    private val delegate: CartApplicationService,
    private val transactions: CartTransactionOperations,
) : CartUseCase {
    constructor(
        delegate: CartApplicationService,
        manager: PlatformTransactionManager,
    ) : this(delegate, SpringCartTransactionOperations(manager))

    override fun setItemQuantity(command: SetCartItemQuantityCommand) =
        when (val start = transactions.read { delegate.inspectSetItemQuantity(command) }) {
            is com.jstore.common.utils.Failure -> start
            is com.jstore.common.utils.Success ->
                when (val value = start.value) {
                    is SetCartItemQuantityStart.Completed ->
                        com.jstore.common.utils.Success(value.view)
                    SetCartItemQuantityStart.RequiresOffer -> {
                        val offer = transactions.withoutTransaction {
                            OfferLookup(delegate.resolveOffer(command.offerId))
                        }
                        executeConvergent {
                            delegate.commitSetItemQuantity(command, offer.identity)
                        }
                    }
                }
        }

    override fun replaceSelection(command: ReplaceCartSelectionCommand) = executeConvergent {
        delegate.replaceSelection(command)
    }

    override fun refresh(buyerId: Long, expectedVersion: Long) =
        when (val start = transactions.read { delegate.startRefresh(buyerId, expectedVersion) }) {
            is com.jstore.common.utils.Failure -> start
            is com.jstore.common.utils.Success ->
                when (val value = start.value) {
                    is CartRefreshStart.Completed -> com.jstore.common.utils.Success(value.view)
                    is CartRefreshStart.RequiresFacts ->
                        when (
                            val facts = transactions.withoutTransaction {
                                delegate.collectFacts(value.cart)
                            }
                        ) {
                            is com.jstore.common.utils.Failure -> facts
                            is com.jstore.common.utils.Success ->
                                transactions.write {
                                    delegate.completeRefresh(value.cart, facts.value)
                                }
                        }
                }
        }

    override fun current(buyerId: Long) = transactions.read { delegate.current(buyerId) }

    private fun <T : Any> executeConvergent(operation: () -> T): T =
        try {
            transactions.write(operation)
        } catch (_: OptimisticLockingFailureException) {
            transactions.write(operation)
        }
}

private data class OfferLookup(val identity: com.jstore.cart.acl.OfferIdentity?)

class TransactionalCartCheckoutSourceQueryService(
    private val delegate: CartApplicationService,
    private val transactions: CartTransactionOperations,
) : CartCheckoutSourceQueryService {
    constructor(
        delegate: CartApplicationService,
        manager: PlatformTransactionManager,
    ) : this(delegate, SpringCartTransactionOperations(manager))

    override fun prepare(query: com.jstore.cart.api.CartCheckoutSourceQuery) =
        when (val start = transactions.read { delegate.startPrepare(query) }) {
            is CartCheckoutPreparationStart.Completed -> start.result
            is CartCheckoutPreparationStart.RequiresFacts ->
                transactions.withoutTransaction { delegate.prepareWithFacts(start.cart) }
        }
}

interface CartTransactionOperations {
    fun <T : Any> read(action: () -> T): T

    fun <T : Any> write(action: () -> T): T

    fun <T : Any> withoutTransaction(action: () -> T): T
}

class SpringCartTransactionOperations(transactionManager: PlatformTransactionManager) :
    CartTransactionOperations {
    private val read =
        TransactionTemplate(transactionManager).apply {
            isReadOnly = true
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
    private val write =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
    private val external =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }

    override fun <T : Any> read(action: () -> T): T = requireNotNull(read.execute { action() })

    override fun <T : Any> write(action: () -> T): T = requireNotNull(write.execute { action() })

    override fun <T : Any> withoutTransaction(action: () -> T): T =
        requireNotNull(external.execute { action() })
}
