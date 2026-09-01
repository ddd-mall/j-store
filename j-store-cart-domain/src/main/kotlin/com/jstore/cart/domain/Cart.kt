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

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.EventRecordingAggregateRoot
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventType
import com.jstore.common.framework.event.newDomainEventId
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Instant

data class CartLine(
    val id: CartLineId,
    val skuId: SkuId,
    val offerId: OfferId,
    val merchantId: MerchantId,
    val quantity: Int,
    val selected: Boolean,
    val addedAt: Instant,
    val modifiedAt: Instant,
)

@DomainEventType(name = "cart.refresh-requested", version = 1)
data class CartRefreshRequestedEvent(
    val cartId: CartId,
    val buyerId: BuyerId,
    val cartVersion: Long,
    val reason: String,
    override val occurredAt: Instant = Instant.now(),
    override val eventId: String = newDomainEventId(),
) : DomainEvent {
    override val eventName = "cart.refresh-requested"
    override val eventVersion = 1
    override val aggregateType = "Cart"
    override val aggregateId
        get() = cartId.value.toString()
}

class Cart(
    override val id: CartId,
    val buyerId: BuyerId,
    val settlementScope: SettlementScope,
    val status: CartStatus,
    lines: List<CartLine>,
    contentVersion: Long,
    val persistenceVersion: Long = 0,
) : EventRecordingAggregateRoot<CartId>() {
    private val mutableLines = lines.toMutableList()
    var contentVersion: Long = contentVersion
        private set

    val lines: List<CartLine>
        get() = mutableLines.toList()

    fun add(
        lineId: CartLineId,
        skuId: SkuId,
        offerId: OfferId,
        merchantId: MerchantId,
        quantity: Int,
        scope: SettlementScope,
        now: Instant = Instant.now(),
    ): Result<CartLine, BusinessError> {
        if (quantity !in 1..999) return Failure(CartErrors.INVALID_QUANTITY)
        if (scope != settlementScope) return Failure(CartErrors.SCOPE_MISMATCH)
        val index = mutableLines.indexOfFirst { it.offerId == offerId }
        val updated =
            if (index >= 0) {
                val existing = mutableLines[index]
                if (existing.quantity + quantity > 999) return Failure(CartErrors.INVALID_QUANTITY)
                existing.copy(quantity = existing.quantity + quantity, modifiedAt = now).also {
                    mutableLines[index] = it
                }
            } else {
                if (mutableLines.size >= 100) return Failure(CartErrors.LINE_LIMIT)
                CartLine(
                        lineId,
                        skuId,
                        offerId,
                        merchantId,
                        quantity,
                        true,
                        now,
                        now,
                    )
                    .also(mutableLines::add)
            }
        changed("ITEM_ADDED", now)
        return Success(updated)
    }

    fun replaceSelection(
        ids: Set<CartLineId>,
        now: Instant = Instant.now(),
    ): Result<Boolean, BusinessError> {
        if (!mutableLines.map { it.id }.containsAll(ids))
            return Failure(CartErrors.UNKNOWN_SELECTION)
        if (mutableLines.all { it.selected == (it.id in ids) }) return Success(false)
        mutableLines.indices.forEach { index ->
            val line = mutableLines[index]
            val selected = line.id in ids
            if (line.selected != selected)
                mutableLines[index] = line.copy(selected = selected, modifiedAt = now)
        }
        changed("SELECTION_CHANGED", now)
        return Success(true)
    }

    private fun changed(reason: String, now: Instant) {
        contentVersion++
        raise(CartRefreshRequestedEvent(id, buyerId, contentVersion, reason, now))
    }

    companion object {
        fun create(id: CartId, buyerId: BuyerId, scope: SettlementScope) =
            Cart(id, buyerId, scope, CartStatus.ACTIVE, emptyList(), 0)
    }
}
