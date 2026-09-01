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

import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Price
import java.time.Instant

enum class AssessmentStatus { COMPLETE, PARTIAL, EMPTY }
enum class LineAssessmentStatus { ELIGIBLE, UNSELECTED, CATALOG_UNAVAILABLE, OFFER_UNAVAILABLE, OUT_OF_STOCK, INSUFFICIENT_STOCK }

data class CartLineCommerceFacts(
    val cartLineId: CartLineId,
    val catalogAvailable: Boolean,
    val offerAvailable: Boolean,
    val unitPrice: Price?,
    val offerVersion: Long?,
    val catalogVersion: Long?,
    val availableToPromise: Int?,
    val spuId: Long?,
    val fulfillmentNodeId: String?,
    val market: String?,
    val channelId: String?,
    val currency: String?,
    val merchantId: Long?,
    val goodsName: String?,
    val skuDescription: String?,
)

data class CartAssessmentLine(
    val cartLineId: CartLineId,
    val status: LineAssessmentStatus,
    val observedUnitPrice: Price?,
    val observedOfferVersion: Long?,
    val observedCatalogVersion: Long?,
    val observedAtp: Int?,
    val amount: Price,
)

class CartAssessment(
    override val id: CartAssessmentId,
    val cartId: CartId,
    val sourceCartVersion: Long,
    val status: AssessmentStatus,
    val estimatedAmount: Price,
    val currency: String,
    val evaluatedAt: Instant,
    val lines: List<CartAssessmentLine>,
) : AggregateRoot<CartAssessmentId>

object CartAssessmentCalculator {
    fun evaluate(id: CartAssessmentId, cart: Cart, facts: List<CartLineCommerceFacts>, now: Instant): CartAssessment {
        val factsByLine = facts.associateBy { it.cartLineId }
        val assessed = cart.lines.map { line ->
            val fact = factsByLine[line.id]
            val status = when {
                !line.selected -> LineAssessmentStatus.UNSELECTED
                fact == null || !fact.catalogAvailable -> LineAssessmentStatus.CATALOG_UNAVAILABLE
                !fact.offerAvailable || fact.unitPrice == null || fact.market != cart.settlementScope.market || fact.channelId != cart.settlementScope.channelId || fact.currency != cart.settlementScope.currency -> LineAssessmentStatus.OFFER_UNAVAILABLE
                fact.availableToPromise == null || fact.availableToPromise == 0 -> LineAssessmentStatus.OUT_OF_STOCK
                fact.availableToPromise < line.quantity -> LineAssessmentStatus.INSUFFICIENT_STOCK
                else -> LineAssessmentStatus.ELIGIBLE
            }
            CartAssessmentLine(line.id, status, fact?.unitPrice, fact?.offerVersion, fact?.catalogVersion, fact?.availableToPromise, if (status == LineAssessmentStatus.ELIGIBLE) fact!!.unitPrice!! * line.quantity else Price.ZERO)
        }
        val amount = Price.sumOf(assessed.map { it.amount })
        val selected = assessed.filter { line -> cart.lines.first { it.id == line.cartLineId }.selected }
        val status = when {
            assessed.none { it.status == LineAssessmentStatus.ELIGIBLE } -> AssessmentStatus.EMPTY
            selected.all { it.status == LineAssessmentStatus.ELIGIBLE } -> AssessmentStatus.COMPLETE
            else -> AssessmentStatus.PARTIAL
        }
        return CartAssessment(id, cart.id, cart.contentVersion, status, amount, cart.settlementScope.currency, now, assessed)
    }
}
