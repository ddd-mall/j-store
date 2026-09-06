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
package com.jstore.cart.service

import com.jstore.cart.acl.CartCommerceFactsService
import com.jstore.cart.acl.OfferIdentity
import com.jstore.cart.api.*
import com.jstore.cart.domain.*
import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.security.MessageDigest
import java.time.Clock

class CartApplicationService(
    private val carts: CartRepository,
    private val assessments: CartAssessmentStore,
    private val commerce: CartCommerceFactsService,
    private val ids: CartIdentityGenerator,
    private val publisher: DomainEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) : CartUseCase, CartCheckoutSourceQueryService {

    override fun setItemQuantity(
        command: SetCartItemQuantityCommand
    ): Result<CartView, BusinessError> {
        return when (val start = inspectSetItemQuantity(command)) {
            is Failure -> start
            is Success ->
                when (val value = start.value) {
                    is SetCartItemQuantityStart.Completed -> Success(value.view)
                    SetCartItemQuantityStart.RequiresOffer ->
                        commitSetItemQuantity(command, resolveOffer(command.offerId))
                }
        }
    }

    fun inspectSetItemQuantity(
        command: SetCartItemQuantityCommand
    ): Result<SetCartItemQuantityStart, BusinessError> {
        val buyerId = BuyerId(command.buyerId)
        val existing = carts.findActiveByBuyerId(buyerId)
        if (
            existing?.hasItemTarget(
                skuId = SkuId(command.skuId),
                offerId = OfferId(command.offerId),
                targetQuantity = command.targetQuantity,
            ) == true
        ) {
            return Success(
                SetCartItemQuantityStart.Completed(
                    view(existing, assessments.findLatestByCart(existing.id))
                )
            )
        }

        return Success(SetCartItemQuantityStart.RequiresOffer)
    }

    fun resolveOffer(offerId: Long): OfferIdentity? = commerce.findOffer(OfferId(offerId))

    fun commitSetItemQuantity(
        command: SetCartItemQuantityCommand,
        identity: OfferIdentity?,
    ): Result<CartView, BusinessError> {
        val buyerId = BuyerId(command.buyerId)
        val existing = carts.findActiveByBuyerId(buyerId)
        if (
            existing?.hasItemTarget(
                skuId = SkuId(command.skuId),
                offerId = OfferId(command.offerId),
                targetQuantity = command.targetQuantity,
            ) == true
        ) {
            return Success(view(existing, assessments.findLatestByCart(existing.id)))
        }

        identity ?: return Failure(CartErrors.OFFER_MISMATCH)

        if (identity.skuId.value != command.skuId) {
            return Failure(CartErrors.OFFER_MISMATCH)
        }
        val cart =
            existing
                ?: Cart.create(
                    CartId(ids.nextId()),
                    buyerId,
                    identity.settlementScope,
                )

        val changed =
            cart
                .setItemQuantity(
                    expectedVersion = command.expectedCartVersion,
                    lineId = CartLineId(ids.nextId()),
                    skuId = SkuId(command.skuId),
                    offerId = OfferId(command.offerId),
                    merchantId = MerchantId(identity.merchantId),
                    targetQuantity = command.targetQuantity,
                    scope = identity.settlementScope,
                    now = clock.instant(),
                )
                .let {
                    when (it) {
                        is Failure -> return Failure(it.error)
                        is Success -> it.value
                    }
                }
        if (!changed) {
            return Success(view(cart, assessments.findLatestByCart(cart.id)))
        }
        carts.save(cart)
        cart.publishPendingEvents(publisher)
        return Success(view(cart, assessments.findLatestByCart(cart.id)))
    }

    override fun replaceSelection(
        command: ReplaceCartSelectionCommand
    ): Result<CartView, BusinessError> {
        val buyerId = BuyerId(command.buyerId)
        val cart = carts.findActiveByBuyerId(buyerId) ?: return Failure(CartErrors.NOT_FOUND)
        val changed =
            cart
                .replaceSelection(
                    expectedVersion = command.expectedCartVersion,
                    ids = command.cartLineIds.map(::CartLineId).toSet(),
                    now = clock.instant(),
                )
                .let {
                    when (it) {
                        is Failure -> return Failure(it.error)
                        is Success -> it.value
                    }
                }
        if (!changed) {
            return Success(view(cart, assessments.findLatestByCart(cart.id)))
        }

        carts.save(cart)
        cart.publishPendingEvents(publisher)
        return Success(view(cart, assessments.findLatestByCart(cart.id)))
    }

    override fun refresh(
        buyerId: Long,
        expectedVersion: Long,
    ): Result<CartView, BusinessError> {
        return when (val start = startRefresh(buyerId, expectedVersion)) {
            is Failure -> start
            is Success ->
                when (val value = start.value) {
                    is CartRefreshStart.Completed -> Success(value.view)
                    is CartRefreshStart.RequiresFacts ->
                        when (val facts = collectFacts(value.cart)) {
                            is Failure -> facts
                            is Success -> completeRefresh(value.cart, facts.value)
                        }
                }
        }
    }

    fun startRefresh(
        buyerId: Long,
        expectedVersion: Long,
    ): Result<CartRefreshStart, BusinessError> {
        val buyer = BuyerId(buyerId)
        val cart = carts.findActiveByBuyerId(buyer) ?: return Failure(CartErrors.NOT_FOUND)

        if (cart.contentVersion != expectedVersion) {
            return Failure(CartErrors.VERSION_CONFLICT)
        }

        val existing = assessments.findByCartAndVersion(cart.id, cart.contentVersion)
        return if (existing == null) {
            Success(CartRefreshStart.RequiresFacts(cart))
        } else {
            Success(CartRefreshStart.Completed(view(cart, existing)))
        }
    }

    fun collectFacts(cart: Cart): Result<List<CartLineCommerceFacts>, BusinessError> =
        try {
            Success(commerce.collect(cart.lines))
        } catch (_: RuntimeException) {
            Failure(CartErrors.REFRESH_UNAVAILABLE)
        }

    fun completeRefresh(
        cart: Cart,
        facts: List<CartLineCommerceFacts>,
    ): Result<CartView, BusinessError> {
        assessments.findByCartAndVersion(cart.id, cart.contentVersion)?.let {
            return Success(view(cart, it))
        }
        if (carts.findById(cart.id)?.contentVersion != cart.contentVersion) {
            return Failure(CartErrors.VERSION_CONFLICT)
        }
        val calculated =
            CartAssessmentCalculator.evaluate(
                id = CartAssessmentId(ids.nextId()),
                cart = cart,
                facts = facts,
                now = clock.instant(),
            )
        return Success(view(cart, assessments.save(calculated)))
    }

    override fun current(buyerId: Long): Result<CartView, BusinessError> {
        val cart =
            carts.findActiveByBuyerId(BuyerId(buyerId)) ?: return Failure(CartErrors.NOT_FOUND)

        return Success(
            value =
                view(
                    cart = cart,
                    assessment = assessments.findLatestByCart(cart.id),
                )
        )
    }

    override fun prepare(query: CartCheckoutSourceQuery): CartCheckoutSourceResult {
        return when (val start = startPrepare(query)) {
            is CartCheckoutPreparationStart.Completed -> start.result
            is CartCheckoutPreparationStart.RequiresFacts -> prepareWithFacts(start.cart)
        }
    }

    fun startPrepare(query: CartCheckoutSourceQuery): CartCheckoutPreparationStart {
        val cart =
            carts.findById(CartId(query.cartId))?.takeIf { it.buyerId.value == query.buyerId }
                ?: return CartCheckoutPreparationStart.Completed(CartCheckoutSourceResult.NotFound)

        if (cart.contentVersion != query.expectedCartVersion) {
            return CartCheckoutPreparationStart.Completed(CartCheckoutSourceResult.VersionConflict)
        }

        return CartCheckoutPreparationStart.RequiresFacts(cart)
    }

    fun prepareWithFacts(cart: Cart): CartCheckoutSourceResult {
        val facts =
            runCatching {
                    commerce.collect(cart.lines)
                }
                .getOrElse {
                    return CartCheckoutSourceResult.Unavailable
                }

        val assessment =
            CartAssessmentCalculator.evaluate(
                id = CartAssessmentId(ids.nextId()),
                cart = cart,
                facts = facts,
                now = clock.instant(),
            )
        val factsByLine = facts.associateBy { it.cartLineId }
        val eligible =
            assessment.lines
                .filter { it.status == LineAssessmentStatus.ELIGIBLE }
                .map { assessed ->
                    val line = cart.lines.first { it.id == assessed.cartLineId }
                    val fact = factsByLine.getValue(line.id)
                    CartCheckoutLine(
                        cartLineId = line.id.value,
                        offerId = line.offerId.value,
                        offerVersion = requireNotNull(fact.offerVersion),
                        spuId = requireNotNull(fact.spuId),
                        skuId = line.skuId.value,
                        quantity = line.quantity,
                        catalogSnapshotVersion = requireNotNull(fact.catalogVersion),
                    )
                }

        if (eligible.isEmpty()) {
            return CartCheckoutSourceResult.NoEligibleLines
        }

        val digest =
            sha256(
                listOf(cart.id.value, cart.contentVersion, cart.settlementScope, eligible)
                    .joinToString("|")
            )
        return CartCheckoutSourceResult.Found(
            CartCheckoutSource(
                cartId = cart.id.value,
                cartVersion = cart.contentVersion,
                cartDigest = digest,
                market = cart.settlementScope.market,
                channelId = cart.settlementScope.channelId,
                currency = cart.settlementScope.currency,
                eligibleLines = eligible,
            )
        )
    }

    private fun view(cart: Cart, assessment: CartAssessment?) =
        CartView(
            cartId = cart.id.value,
            contentVersion = cart.contentVersion,
            market = cart.settlementScope.market,
            channelId = cart.settlementScope.channelId,
            currency = cart.settlementScope.currency,
            lines = cart.lines,
            assessment =
                assessment?.let {
                    CartAssessmentView(
                        sourceCartVersion = it.sourceCartVersion,
                        status =
                            if (it.sourceCartVersion == cart.contentVersion) {
                                it.status.name
                            } else {
                                "STALE"
                            },
                        amountFen = it.estimatedAmount.fen,
                        currency = it.currency,
                        lines = it.lines,
                    )
                },
        )

    private fun sha256(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }
}

class CartRefreshRequestedHandler(
    private val service: CartApplicationService,
    private val carts: CartRepository,
) : DomainEventListener<CartRefreshRequestedEvent> {
    override fun listenerId() = "cart.refresh-requested.v1"

    fun start(event: CartRefreshRequestedEvent): Cart? =
        carts.findById(event.cartId)?.takeIf { it.contentVersion == event.cartVersion }

    fun collect(cart: Cart): List<CartLineCommerceFacts> =
        when (val result = service.collectFacts(cart)) {
            is Failure -> throw com.jstore.common.errors.BusinessErrorException(result.error)
            is Success -> result.value
        }

    fun complete(cart: Cart, facts: List<CartLineCommerceFacts>) {
        when (val result = service.completeRefresh(cart, facts)) {
            is Failure ->
                if (result.error != CartErrors.VERSION_CONFLICT) {
                    throw com.jstore.common.errors.BusinessErrorException(result.error)
                }
            is Success -> Unit
        }
    }

    override fun onDomainEvent(event: CartRefreshRequestedEvent) {
        val cart = start(event) ?: return
        complete(cart, collect(cart))
    }
}
