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
import com.jstore.cart.api.*
import com.jstore.cart.domain.*
import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventListener
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import java.security.MessageDigest
import java.time.Clock

class CartApplicationService(
    private val carts: CartRepository,
    private val assessments: CartAssessmentRepository,
    private val receipts: CartRequestReceiptRepository,
    private val commerce: CartCommerceFactsService,
    private val ids: CartIdentityGenerator,
    private val publisher: DomainEventPublisher,
    private val clock: Clock = Clock.systemUTC(),
) : CartUseCase, CartCheckoutSourceQueryService {
    override fun add(command: AddCartItemCommand): Result<CartView, BusinessError> {
        val buyerId = BuyerId(command.buyerId)
        if (command.requestId.isBlank() || command.requestId.length > 128) return Failure(CartErrors.REQUEST_CONFLICT)
        val requestDigest = sha256("${command.skuId}|${command.offerId}|${command.quantity}|${command.expectedCartVersion}")
        receipts.findByBuyerAndRequest(buyerId, command.requestId)?.let { receipt ->
            if (receipt.requestDigest != requestDigest) return Failure(CartErrors.REQUEST_CONFLICT)
            val existing = carts.findById(receipt.cartId)?.takeIf { it.buyerId == buyerId } ?: return Failure(CartErrors.NOT_FOUND)
            return Success(view(existing, assessments.findLatestByCart(existing.id)))
        }
        val identity = commerce.findOffer(OfferId(command.offerId)) ?: return Failure(CartErrors.OFFER_MISMATCH)
        if (identity.skuId.value != command.skuId) return Failure(CartErrors.OFFER_MISMATCH)
        val cart = carts.findActiveByBuyerId(buyerId) ?: Cart.create(CartId(ids.nextId()), buyerId, identity.settlementScope)
        if (command.expectedCartVersion != null && command.expectedCartVersion != cart.contentVersion) return Failure(CartErrors.VERSION_CONFLICT)
        cart.add(CartLineId(ids.nextId()), SkuId(command.skuId), OfferId(command.offerId), MerchantId(identity.merchantId), command.quantity, identity.settlementScope, clock.instant()).onFailure { return Failure(it) }
        carts.save(cart)
        receipts.save(CartRequestReceipt(CartRequestReceiptId("${buyerId.value}:${command.requestId}"), buyerId, command.requestId, requestDigest, cart.id, cart.contentVersion))
        cart.publishPendingEvents(publisher)
        return refreshCart(cart).mapView(cart)
    }

    override fun replaceSelection(command: ReplaceCartSelectionCommand): Result<CartView, BusinessError> {
        val buyerId = BuyerId(command.buyerId)
        val digest = sha256("selection|${command.expectedCartVersion}|${command.cartLineIds.sorted()}")
        duplicate(buyerId, command.requestId, digest)?.let { return it }
        val cart = carts.findActiveByBuyerId(buyerId) ?: return Failure(CartErrors.NOT_FOUND)
        if (cart.contentVersion != command.expectedCartVersion) return Failure(CartErrors.VERSION_CONFLICT)
        cart.replaceSelection(command.cartLineIds.map(::CartLineId).toSet(), clock.instant()).onFailure { return Failure(it) }
        carts.save(cart)
        saveReceipt(buyerId, command.requestId, digest, cart)
        cart.publishPendingEvents(publisher)
        return refreshCart(cart).mapView(cart)
    }

    override fun refresh(buyerId: Long, requestId: String, expectedVersion: Long): Result<CartView, BusinessError> {
        val buyer = BuyerId(buyerId)
        val digest = sha256("refresh|$expectedVersion")
        duplicate(buyer, requestId, digest)?.let { return it }
        val cart = carts.findActiveByBuyerId(buyer) ?: return Failure(CartErrors.NOT_FOUND)
        if (cart.contentVersion != expectedVersion) return Failure(CartErrors.VERSION_CONFLICT)
        val result = refreshCart(cart).mapView(cart)
        if (result is Success) saveReceipt(buyer, requestId, digest, cart)
        return result
    }

    override fun current(buyerId: Long): Result<CartView, BusinessError> {
        val cart = carts.findActiveByBuyerId(BuyerId(buyerId)) ?: return Failure(CartErrors.NOT_FOUND)
        return Success(view(cart, assessments.findLatestByCart(cart.id)))
    }

    override fun prepare(query: CartCheckoutSourceQuery): CartCheckoutSourceResult {
        val cart = carts.findById(CartId(query.cartId))?.takeIf { it.buyerId.value == query.buyerId } ?: return CartCheckoutSourceResult.NotFound
        if (cart.contentVersion != query.expectedCartVersion) return CartCheckoutSourceResult.VersionConflict
        val facts = runCatching { commerce.collect(cart.lines) }.getOrElse { return CartCheckoutSourceResult.Unavailable }
        val assessment = CartAssessmentCalculator.evaluate(CartAssessmentId(ids.nextId()), cart, facts, clock.instant())
        val factsByLine = facts.associateBy { it.cartLineId }
        val eligible = assessment.lines.filter { it.status == LineAssessmentStatus.ELIGIBLE }.map { assessed ->
            val line = cart.lines.first { it.id == assessed.cartLineId }
            val fact = factsByLine.getValue(line.id)
            CartCheckoutLine(line.id.value, line.offerId.value, requireNotNull(fact.offerVersion), requireNotNull(fact.spuId), line.skuId.value, line.quantity, requireNotNull(fact.catalogVersion))
        }
        if (eligible.isEmpty()) return CartCheckoutSourceResult.NoEligibleLines
        val digest = sha256(listOf(cart.id.value, cart.contentVersion, cart.settlementScope, eligible).joinToString("|"))
        return CartCheckoutSourceResult.Found(CartCheckoutSource(cart.id.value, cart.contentVersion, digest, cart.settlementScope.market, cart.settlementScope.channelId, cart.settlementScope.currency, eligible))
    }

    internal fun refreshCart(cart: Cart): Result<CartAssessment, BusinessError> {
        return try {
            val existing = assessments.findByCartAndVersion(cart.id, cart.contentVersion)
            if (existing != null) return Success(existing)
            val calculated = CartAssessmentCalculator.evaluate(CartAssessmentId(ids.nextId()), cart, commerce.collect(cart.lines), clock.instant())
            if (carts.findById(cart.id)?.contentVersion != cart.contentVersion) Failure(CartErrors.VERSION_CONFLICT)
            else Success(assessments.save(calculated))
        } catch (_: RuntimeException) {
            assessments.findByCartAndVersion(cart.id, cart.contentVersion)?.let(::Success)
                ?: Failure(CartErrors.REFRESH_UNAVAILABLE)
        }
    }

    private fun Result<CartAssessment, BusinessError>.mapView(cart: Cart): Result<CartView, BusinessError> = when (this) {
        is Failure -> this
        is Success -> Success(view(cart, value))
    }

    private fun view(cart: Cart, assessment: CartAssessment?) = CartView(cart.id.value, cart.contentVersion, cart.settlementScope.market, cart.settlementScope.channelId, cart.settlementScope.currency, cart.lines, assessment?.let { CartAssessmentView(it.sourceCartVersion, if (it.sourceCartVersion == cart.contentVersion) it.status.name else "STALE", it.estimatedAmount.fen, it.currency, it.lines) })
    private fun duplicate(buyerId: BuyerId, requestId: String, digest: String): Result<CartView, BusinessError>? {
        if (requestId.isBlank() || requestId.length > 128) return Failure(CartErrors.REQUEST_CONFLICT)
        val receipt = receipts.findByBuyerAndRequest(buyerId, requestId) ?: return null
        if (receipt.requestDigest != digest) return Failure(CartErrors.REQUEST_CONFLICT)
        val cart = carts.findById(receipt.cartId)?.takeIf { it.buyerId == buyerId } ?: return Failure(CartErrors.NOT_FOUND)
        return Success(view(cart, assessments.findLatestByCart(cart.id)))
    }
    private fun saveReceipt(buyerId: BuyerId, requestId: String, digest: String, cart: Cart) {
        receipts.save(CartRequestReceipt(CartRequestReceiptId("${buyerId.value}:$requestId"), buyerId, requestId, digest, cart.id, cart.contentVersion))
    }
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

class CartRefreshRequestedHandler(private val service: CartApplicationService, private val carts: CartRepository) : DomainEventListener<CartRefreshRequestedEvent> {
    override fun listenerId() = "cart.refresh-requested.v1"
    override fun onDomainEvent(event: CartRefreshRequestedEvent) {
        carts.findById(event.cartId)?.takeIf { it.contentVersion == event.cartVersion }?.let { service.refreshCart(it) }
    }
}
