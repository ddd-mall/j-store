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
package com.jstore.cart.domain

import com.jstore.cart.domain.persistence.*
import com.jstore.common.properties.Price
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class CartRepositoryImpl(private val jpa: CartPOJpaRepository) : CartRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: Cart): Cart = toDomain(jpa.save(toPO(aggregate)))

    override fun findById(id: CartId): Cart? = jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findActiveByBuyerId(buyerId: BuyerId): Cart? =
        jpa.findByBuyerIdAndStatus(buyerId.value, CartStatus.ACTIVE)?.let(::toDomain)

    private fun toPO(cart: Cart): CartPO {
        val po =
            CartPO(
                id = cart.id.value,
                buyerId = cart.buyerId.value,
                status = cart.status,
                market = cart.settlementScope.market,
                channelId = cart.settlementScope.channelId,
                currency = cart.settlementScope.currency,
                contentVersion = cart.contentVersion,
                persistenceVersion = cart.persistenceVersion,
            )
        po.lines =
            cart.lines
                .map {
                    CartLinePO(
                        id = it.id.value,
                        cart = po,
                        skuId = it.skuId.value,
                        offerId = it.offerId.value,
                        merchantId = it.merchantId.value,
                        quantity = it.quantity,
                        selected = it.selected,
                        addedAt = it.addedAt,
                        modifiedAt = it.modifiedAt,
                    )
                }
                .toMutableList()
        return po
    }

    private fun toDomain(po: CartPO) =
        Cart(
            id = CartId(po.id),
            buyerId = BuyerId(po.buyerId),
            settlementScope = SettlementScope(po.market, po.channelId, po.currency),
            status = po.status,
            lines =
                po.lines.map {
                    CartLine(
                        id = CartLineId(it.id),
                        skuId = SkuId(it.skuId),
                        offerId = OfferId(it.offerId),
                        merchantId = MerchantId(it.merchantId),
                        quantity = it.quantity,
                        selected = it.selected,
                        addedAt = it.addedAt,
                        modifiedAt = it.modifiedAt,
                    )
                },
            contentVersion = po.contentVersion,
            persistenceVersion = po.persistenceVersion,
        )
}

@Repository
class CartAssessmentStoreImpl(private val jpa: CartAssessmentPOJpaRepository) :
    CartAssessmentStore {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(assessment: CartAssessment): CartAssessment =
        toDomain(jpa.save(toPO(assessment)))

    override fun findById(id: CartAssessmentId): CartAssessment? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByCartAndVersion(cartId: CartId, version: Long) =
        jpa.findByCartIdAndSourceCartVersion(cartId.value, version)?.let(::toDomain)

    override fun findLatestByCart(cartId: CartId) =
        jpa.findFirstByCartIdOrderBySourceCartVersionDesc(cartId.value)?.let(::toDomain)

    private fun toPO(a: CartAssessment): CartAssessmentPO {
        val po =
            CartAssessmentPO(
                id = a.id.value,
                cartId = a.cartId.value,
                sourceCartVersion = a.sourceCartVersion,
                status = a.status,
                amountFen = a.estimatedAmount.fen,
                currency = a.currency,
                evaluatedAt = a.evaluatedAt,
            )
        po.lines =
            a.lines
                .map {
                    CartAssessmentLinePO(
                        id = 0,
                        assessment = po,
                        cartLineId = it.cartLineId.value,
                        status = it.status,
                        unitPriceFen = it.observedUnitPrice?.fen,
                        offerVersion = it.observedOfferVersion,
                        catalogVersion = it.observedCatalogVersion,
                        observedAtp = it.observedAtp,
                        amountFen = it.amount.fen,
                    )
                }
                .toMutableList()
        return po
    }

    private fun toDomain(po: CartAssessmentPO) =
        CartAssessment(
            id = CartAssessmentId(po.id),
            cartId = CartId(po.cartId),
            sourceCartVersion = po.sourceCartVersion,
            status = po.status,
            estimatedAmount = Price.ofFen(po.amountFen),
            currency = po.currency,
            evaluatedAt = po.evaluatedAt,
            lines =
                po.lines.map {
                    CartAssessmentLine(
                        cartLineId = CartLineId(it.cartLineId),
                        status = it.status,
                        observedUnitPrice = it.unitPriceFen?.let(Price::ofFen),
                        observedOfferVersion = it.offerVersion,
                        observedCatalogVersion = it.catalogVersion,
                        observedAtp = it.observedAtp,
                        amount = Price.ofFen(it.amountFen),
                    )
                },
        )
}

@Repository
class CartRequestReceiptStoreImpl(private val jpa: CartRequestReceiptPOJpaRepository) :
    CartRequestReceiptStore {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(receipt: CartRequestReceipt): CartRequestReceipt =
        toDomain(
            po =
                jpa.save(
                    CartRequestReceiptPO(
                        id = receipt.id.value,
                        buyerId = receipt.buyerId.value,
                        requestId = receipt.requestId,
                        requestDigest = receipt.requestDigest,
                        cartId = receipt.cartId.value,
                        cartVersion = receipt.cartVersion,
                    )
                )
        )

    override fun findById(id: CartRequestReceiptId) =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByBuyerAndRequest(buyerId: BuyerId, requestId: String) =
        jpa.findByBuyerIdAndRequestId(buyerId.value, requestId)?.let(::toDomain)

    private fun toDomain(po: CartRequestReceiptPO) =
        CartRequestReceipt(
            id = CartRequestReceiptId(po.id),
            buyerId = BuyerId(po.buyerId),
            requestId = po.requestId,
            requestDigest = po.requestDigest,
            cartId = CartId(po.cartId),
            cartVersion = po.cartVersion,
        )
}
