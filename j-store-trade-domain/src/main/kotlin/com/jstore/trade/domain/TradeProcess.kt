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
package com.jstore.trade.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Instant

data class TradeProcessId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "Trade process ID must be positive" }
    }
}

data class TradeItemSnapshot(
    val offerId: Long,
    val storeId: Long,
    val spuId: Long,
    val skuId: Long,
    val quantity: Int,
    val catalogSnapshotVersion: Long,
    val offerVersion: Long,
    val fulfillmentNodeId: String,
    val channelId: String,
    val unitPrice: Price,
) {
    init {
        require(offerId > 0 && storeId > 0 && spuId > 0 && skuId > 0) {
            "Trade item identifiers must be positive"
        }
        require(quantity > 0) { "Trade item quantity must be positive" }
        require(catalogSnapshotVersion > 0 && offerVersion > 0) {
            "Trade item versions must be positive"
        }
        require(fulfillmentNodeId.isNotBlank()) { "Fulfillment node must not be blank" }
        require(channelId.isNotBlank()) { "Channel must not be blank" }
    }
}

data class TradeAuthorization(
    val authorizationId: String,
    val offerId: Long,
    val expiresAt: Instant,
) {
    init {
        require(authorizationId.isNotBlank()) { "Authorization ID must not be blank" }
        require(offerId > 0) { "Offer ID must be positive" }
    }
}

enum class TradeProcessStatus {
    AUTHORIZING,
    RESERVING,
    COMMITTED,
    PAID,
    FAILED,
    CLOSED,
}

class TradeProcess(
    override val id: TradeProcessId,
    val orderId: Long,
    val merchantId: Long,
    items: List<TradeItemSnapshot>,
    val payableAmount: Price,
    val currency: String,
    status: TradeProcessStatus,
    authorizations: List<TradeAuthorization> = emptyList(),
    reservationIds: List<String> = emptyList(),
    reservationExpiresAt: Instant? = null,
    failureReason: String? = null,
    closeReason: String? = null,
    val createdAt: Instant = Instant.now(),
    updatedAt: Instant = createdAt,
    val persistenceVersion: Long = 0,
) : AggregateRoot<TradeProcessId> {
    val items: List<TradeItemSnapshot> = items.toList()

    var status: TradeProcessStatus = status
        private set

    var authorizations: List<TradeAuthorization> = authorizations.sortedBy { it.offerId }
        private set

    var reservationIds: List<String> = reservationIds.sorted()
        private set

    var reservationExpiresAt: Instant? = reservationExpiresAt
        private set

    var failureReason: String? = failureReason
        private set

    var closeReason: String? = closeReason
        private set

    var updatedAt: Instant = updatedAt
        private set

    init {
        require(orderId > 0 && merchantId > 0) { "Order and merchant IDs must be positive" }
        require(this.items.isNotEmpty()) { "Trade must contain at least one item" }
        require(this.items.map { it.offerId }.distinct().size == this.items.size) {
            "A trade can contain each offer only once"
        }
        require(currency.isNotBlank()) { "Currency must not be blank" }
        require(Price.sumOf(this.items.map { it.unitPrice * it.quantity }) == payableAmount) {
            "Payable amount must equal the immutable item snapshot total"
        }
        validateRestoredState()
    }

    fun recordSaleAuthorized(
        authorizations: List<TradeAuthorization>
    ): Result<Boolean, BusinessError> {
        val normalized = authorizations.sortedBy { it.offerId }
        if (status == TradeProcessStatus.RESERVING && this.authorizations == normalized) {
            return Success(false)
        }
        if (status != TradeProcessStatus.AUTHORIZING) return illegalState("record authorization")

        val expectedOffers = items.map { it.offerId }.toSet()
        val actualOffers = normalized.map { it.offerId }
        if (
            normalized.isEmpty() ||
                actualOffers.size != actualOffers.distinct().size ||
                actualOffers.toSet() != expectedOffers ||
                normalized.map { it.authorizationId }.distinct().size != normalized.size
        ) {
            return Failure(TradeErrors.INVALID_AUTHORIZATION)
        }

        this.authorizations = normalized
        status = TradeProcessStatus.RESERVING
        touch()
        return Success(true)
    }

    fun recordInventoryReserved(
        reservationIds: List<String>,
        expiresAt: Instant,
    ): Result<Boolean, BusinessError> {
        val normalized = reservationIds.sorted()
        if (
            status == TradeProcessStatus.COMMITTED &&
                this.reservationIds == normalized &&
                reservationExpiresAt == expiresAt
        ) {
            return Success(false)
        }
        if (status != TradeProcessStatus.RESERVING) return illegalState("record inventory")
        if (
            normalized.isEmpty() ||
                normalized.any { it.isBlank() } ||
                normalized.distinct().size != normalized.size ||
                expiresAt > authorizations.minOf { it.expiresAt }
        ) {
            return Failure(TradeErrors.INVALID_RESERVATION)
        }

        this.reservationIds = normalized
        reservationExpiresAt = expiresAt
        status = TradeProcessStatus.COMMITTED
        touch()
        return Success(true)
    }

    fun fail(reason: String): Result<Boolean, BusinessError> {
        if (status == TradeProcessStatus.FAILED && failureReason == reason) return Success(false)
        if (status !in setOf(TradeProcessStatus.AUTHORIZING, TradeProcessStatus.RESERVING)) {
            return illegalState("fail trade")
        }
        if (reason.isBlank()) return Failure(TradeErrors.INVALID_REASON)

        failureReason = reason
        status = TradeProcessStatus.FAILED
        touch()
        return Success(true)
    }

    fun close(reason: String): Result<Boolean, BusinessError> {
        if (status == TradeProcessStatus.CLOSED || status == TradeProcessStatus.FAILED) {
            return Success(false)
        }
        if (
            status !in
                setOf(
                    TradeProcessStatus.AUTHORIZING,
                    TradeProcessStatus.RESERVING,
                    TradeProcessStatus.COMMITTED,
                )
        ) {
            return illegalState("close trade")
        }
        if (reason.isBlank()) return Failure(TradeErrors.INVALID_REASON)

        closeReason = reason
        status = TradeProcessStatus.CLOSED
        touch()
        return Success(true)
    }

    fun markPaid(): Result<Boolean, BusinessError> {
        if (status == TradeProcessStatus.PAID) return Success(false)
        if (status != TradeProcessStatus.COMMITTED) return illegalState("mark paid")

        status = TradeProcessStatus.PAID
        touch()
        return Success(true)
    }

    fun matchesStartSnapshot(
        orderId: Long,
        merchantId: Long,
        items: List<TradeItemSnapshot>,
        payableAmount: Price,
        currency: String,
    ): Boolean =
        this.orderId == orderId &&
            this.merchantId == merchantId &&
            this.items == items &&
            this.payableAmount == payableAmount &&
            this.currency == currency

    private fun touch() {
        updatedAt = Instant.now()
    }

    private fun illegalState(action: String): Failure<BusinessError> =
        Failure(TradeErrors.ILLEGAL_STATE.msg("Cannot $action while trade is $status"))

    private fun validateRestoredState() {
        require(updatedAt >= createdAt) { "Updated time cannot precede created time" }
        require(persistenceVersion >= 0) { "Persistence version cannot be negative" }
        when (status) {
            TradeProcessStatus.AUTHORIZING -> require(authorizations.isEmpty())
            TradeProcessStatus.RESERVING -> require(authorizations.isNotEmpty())
            TradeProcessStatus.COMMITTED,
            TradeProcessStatus.PAID ->
                require(
                    authorizations.isNotEmpty() &&
                        reservationIds.isNotEmpty() &&
                        reservationExpiresAt != null
                )
            TradeProcessStatus.FAILED -> require(!failureReason.isNullOrBlank())
            TradeProcessStatus.CLOSED -> require(!closeReason.isNullOrBlank())
        }
    }

    companion object {
        fun start(
            id: TradeProcessId,
            orderId: Long,
            merchantId: Long,
            items: List<TradeItemSnapshot>,
            payableAmount: Price,
            currency: String,
        ): TradeProcess =
            TradeProcess(
                id = id,
                orderId = orderId,
                merchantId = merchantId,
                items = items,
                payableAmount = payableAmount,
                currency = currency,
                status = TradeProcessStatus.AUTHORIZING,
            )
    }
}
