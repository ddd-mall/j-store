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
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.time.Instant

data class TradeId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "Trade ID must be positive" }
    }
}

data class TradeOrderPlanId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "Trade order plan ID must be positive" }
    }
}

data class SettlementPlanId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "Settlement plan ID must be positive" }
    }
}

enum class PartyType {
    INDIVIDUAL,
    ORGANIZATION,
}

data class BuyerPartySnapshot(val partyType: PartyType, val partyId: Long) {
    init {
        require(partyId > 0) { "Buyer party ID must be positive" }
    }
}

data class TradeBuyerProfileSnapshot(
    val displayName: String,
    val phone: String?,
) {
    init {
        require(displayName.isNotBlank())
    }
}

data class TradeRecipientSnapshot(
    val name: String,
    val countryCode: String,
    val phone: String?,
    val email: String?,
    val districtCode: String,
    val detailAddress: String,
    val shippingAddress: I18nGeoAddress,
    val postalCode: String? = null,
    val customsFields: Map<String, String> = emptyMap(),
) {
    init {
        require(name.isNotBlank() && countryCode.isNotBlank() && districtCode.isNotBlank())
        require(detailAddress.isNotBlank())
        require(!phone.isNullOrBlank() || !email.isNullOrBlank())
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
    val goodsName: String,
    val skuDescription: String,
) {
    init {
        require(offerId > 0 && storeId > 0 && spuId > 0 && skuId > 0 && quantity > 0)
        require(catalogSnapshotVersion > 0 && offerVersion > 0)
        require(fulfillmentNodeId.isNotBlank() && channelId.isNotBlank() && unitPrice > Price.ZERO)
        require(goodsName.isNotBlank() && skuDescription.isNotBlank())
    }
}

data class TradeAuthorization(
    val authorizationId: String,
    val offerId: Long,
    val expiresAt: Instant,
) {
    init {
        require(authorizationId.isNotBlank() && offerId > 0)
    }
}

enum class TradeMode {
    NORMAL,
    PRESALE,
    CROWDFUNDING,
}

data class CommitmentPolicySnapshot(val tradeMode: TradeMode)

enum class SettlementMode {
    PREPAID,
    DEPOSIT_BALANCE,
    OPEN_ACCOUNT,
}

enum class FulfillmentReleaseRule {
    FULL_PAYMENT,
    DEPOSIT_PAID,
    CREDIT_APPROVED,
}

enum class InstallmentPurpose {
    FULL,
    DEPOSIT,
    BALANCE,
}

data class PaymentInstallmentSnapshot(
    val installmentId: String,
    val purpose: InstallmentPurpose,
    val amount: Price,
) {
    init {
        require(installmentId.isNotBlank() && amount > Price.ZERO)
    }
}

data class SettlementTermsSnapshot(
    val mode: SettlementMode,
    val fulfillmentReleaseRule: FulfillmentReleaseRule,
    val installments: List<PaymentInstallmentSnapshot>,
) {
    init {
        require(installments.map { it.installmentId }.distinct().size == installments.size)
        when (mode) {
            SettlementMode.PREPAID ->
                require(
                    installments.size == 1 &&
                        installments.single().purpose == InstallmentPurpose.FULL
                )
            SettlementMode.DEPOSIT_BALANCE ->
                require(
                    installments.map { it.purpose }.toSet() ==
                        setOf(InstallmentPurpose.DEPOSIT, InstallmentPurpose.BALANCE)
                )
            SettlementMode.OPEN_ACCOUNT -> require(installments.isEmpty())
        }
    }
}

enum class TradeOrderPlanStatus {
    AUTHORIZING,
    RESERVING,
    RESERVED,
    ORDER_CREATING,
    ORDER_CREATED,
    FAILED,
    CLOSED,
}

class TradeOrderPlan(
    override val id: TradeOrderPlanId,
    val merchantId: Long,
    val fulfillmentGroup: String,
    items: List<TradeItemSnapshot>,
    val payableAmount: Price,
    status: TradeOrderPlanStatus = TradeOrderPlanStatus.AUTHORIZING,
    authorizations: List<TradeAuthorization> = emptyList(),
    reservationIds: List<String> = emptyList(),
    reservationExpiresAt: Instant? = null,
    orderId: Long? = null,
) : com.jstore.common.framework.Entity<TradeOrderPlanId> {
    val items: List<TradeItemSnapshot> = items.toList()
    var status: TradeOrderPlanStatus = status
        private set

    var authorizations: List<TradeAuthorization> = authorizations.toList()
        private set

    var reservationIds: List<String> = reservationIds.toList()
        private set

    var reservationExpiresAt: Instant? = reservationExpiresAt
        private set

    var orderId: Long? = orderId
        private set

    init {
        require(merchantId > 0 && fulfillmentGroup.isNotBlank() && this.items.isNotEmpty())
        require(Price.sumOf(this.items.map { it.unitPrice * it.quantity }) == payableAmount)
    }

    internal fun recordSaleAuthorized(
        values: List<TradeAuthorization>
    ): Result<Boolean, BusinessError> {
        val normalized = values.sortedBy { it.offerId }
        if (status == TradeOrderPlanStatus.RESERVING && authorizations == normalized)
            return Success(false)
        if (status != TradeOrderPlanStatus.AUTHORIZING) return illegal("record authorization")
        val offers = items.map { it.offerId }.toSet()
        if (normalized.isEmpty() || normalized.map { it.offerId }.toSet() != offers) {
            return Failure(TradeErrors.INVALID_AUTHORIZATION)
        }
        authorizations = normalized
        status = TradeOrderPlanStatus.RESERVING
        return Success(true)
    }

    internal fun recordInventoryReserved(
        ids: List<String>,
        expiresAt: Instant,
    ): Result<Boolean, BusinessError> {
        val normalized = ids.sorted()
        if (
            status == TradeOrderPlanStatus.RESERVED &&
                reservationIds == normalized &&
                reservationExpiresAt == expiresAt
        )
            return Success(false)
        if (status != TradeOrderPlanStatus.RESERVING) return illegal("record inventory")
        if (
            normalized.isEmpty() ||
                normalized.any { it.isBlank() } ||
                normalized.distinct().size != normalized.size ||
                expiresAt > authorizations.minOf { it.expiresAt }
        ) {
            return Failure(TradeErrors.INVALID_RESERVATION)
        }
        reservationIds = normalized
        reservationExpiresAt = expiresAt
        status = TradeOrderPlanStatus.RESERVED
        return Success(true)
    }

    internal fun startOrderCreation() {
        require(status == TradeOrderPlanStatus.RESERVED)
        status = TradeOrderPlanStatus.ORDER_CREATING
    }

    internal fun recordOrderCreated(value: Long): Result<Boolean, BusinessError> {
        if (status == TradeOrderPlanStatus.ORDER_CREATED) {
            return if (orderId == value) Success(false) else illegal("replace created order")
        }
        if (status != TradeOrderPlanStatus.ORDER_CREATING || value <= 0)
            return illegal("record created order")
        orderId = value
        status = TradeOrderPlanStatus.ORDER_CREATED
        return Success(true)
    }

    internal fun fail(): Boolean {
        if (status == TradeOrderPlanStatus.FAILED) return false
        if (status in setOf(TradeOrderPlanStatus.ORDER_CREATED, TradeOrderPlanStatus.CLOSED)) {
            return false
        }
        status = TradeOrderPlanStatus.FAILED
        return true
    }

    internal fun closeCreatedOrder(): Boolean {
        if (status == TradeOrderPlanStatus.CLOSED) return false
        if (status != TradeOrderPlanStatus.ORDER_CREATED) return false
        status = TradeOrderPlanStatus.CLOSED
        return true
    }

    private fun illegal(action: String): Failure<BusinessError> =
        Failure(TradeErrors.ILLEGAL_STATE.msg("Cannot $action while order plan is $status"))
}

enum class TradeStatus {
    AUTHORIZING,
    RESERVING,
    CREATING_ORDERS,
    SETTLEMENT_PREPARING,
    SETTLEMENT_READY,
    PAID,
    FAILED,
    CLOSING,
    CLOSED,
}

class Trade(
    override val id: TradeId,
    val checkoutRequestId: String,
    val requestDigest: String,
    val buyerParty: BuyerPartySnapshot,
    val buyerProfile: TradeBuyerProfileSnapshot,
    val actingPrincipalId: Long,
    val recipient: TradeRecipientSnapshot,
    orderPlans: List<TradeOrderPlan>,
    val payableAmount: Price,
    val currency: String,
    val commitmentPolicy: CommitmentPolicySnapshot,
    val settlementTerms: SettlementTermsSnapshot,
    status: TradeStatus = TradeStatus.AUTHORIZING,
    settlementPlanId: SettlementPlanId? = null,
    failureReason: String? = null,
    val createdAt: Instant = Instant.now(),
    updatedAt: Instant = createdAt,
    val persistenceVersion: Long = 0,
) : AggregateRoot<TradeId> {
    val orderPlans: List<TradeOrderPlan> = orderPlans.toList()
    var status: TradeStatus = status
        private set

    var settlementPlanId: SettlementPlanId? = settlementPlanId
        private set

    var failureReason: String? = failureReason
        private set

    var updatedAt: Instant = updatedAt
        private set

    init {
        require(
            checkoutRequestId.isNotBlank() && requestDigest.isNotBlank() && actingPrincipalId > 0
        )
        require(
            this.orderPlans.isNotEmpty() &&
                this.orderPlans.map { it.id }.distinct().size == this.orderPlans.size
        )
        require(
            currency.isNotBlank() &&
                Price.sumOf(this.orderPlans.map { it.payableAmount }) == payableAmount
        )
        require(
            Price.sumOf(settlementTerms.installments.map { it.amount }) == payableAmount ||
                settlementTerms.mode == SettlementMode.OPEN_ACCOUNT
        )
    }

    fun plan(id: TradeOrderPlanId): TradeOrderPlan =
        orderPlans.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown order plan ${id.value}")

    fun recordSaleAuthorized(
        id: TradeOrderPlanId,
        values: List<TradeAuthorization>,
    ): Result<Boolean, BusinessError> {
        if (status !in setOf(TradeStatus.AUTHORIZING, TradeStatus.RESERVING))
            return illegal("record authorization")
        val result = plan(id).recordSaleAuthorized(values)
        if (result is Success && result.value) {
            status = TradeStatus.RESERVING
            touch()
        }
        return result
    }

    fun recordInventoryReserved(
        id: TradeOrderPlanId,
        reservationIds: List<String>,
        expiresAt: Instant,
    ): Result<Boolean, BusinessError> {
        if (status != TradeStatus.RESERVING) return illegal("record inventory")
        val result = plan(id).recordInventoryReserved(reservationIds, expiresAt)
        if (result is Success && result.value) touch()
        return result
    }

    fun startOrderCreation(): Result<Boolean, BusinessError> {
        if (status == TradeStatus.CREATING_ORDERS) return Success(false)
        if (
            status != TradeStatus.RESERVING ||
                orderPlans.any { it.status != TradeOrderPlanStatus.RESERVED }
        )
            return illegal("start order creation")
        orderPlans.forEach { it.startOrderCreation() }
        status = TradeStatus.CREATING_ORDERS
        touch()
        return Success(true)
    }

    fun recordOrderCreated(
        planId: TradeOrderPlanId,
        orderId: Long,
    ): Result<Boolean, BusinessError> {
        if (status != TradeStatus.CREATING_ORDERS) return illegal("record created order")
        val result = plan(planId).recordOrderCreated(orderId)
        if (result is Success && result.value) touch()
        return result
    }

    fun prepareSettlement(id: SettlementPlanId): Result<Boolean, BusinessError> {
        if (status == TradeStatus.SETTLEMENT_PREPARING && settlementPlanId == id)
            return Success(false)
        if (
            status != TradeStatus.CREATING_ORDERS ||
                orderPlans.any { it.status != TradeOrderPlanStatus.ORDER_CREATED }
        )
            return illegal("prepare settlement")
        settlementPlanId = id
        status = TradeStatus.SETTLEMENT_PREPARING
        touch()
        return Success(true)
    }

    fun fail(planId: TradeOrderPlanId, reason: String): Result<Boolean, BusinessError> {
        if (reason.isBlank()) return Failure(TradeErrors.INVALID_REASON)
        if (status == TradeStatus.FAILED) {
            return if (failureReason == reason) Success(false) else illegal("replace failure")
        }
        if (
            status in
                setOf(
                    TradeStatus.SETTLEMENT_PREPARING,
                    TradeStatus.SETTLEMENT_READY,
                    TradeStatus.PAID,
                    TradeStatus.CLOSED,
                )
        ) {
            return illegal("fail trade")
        }
        val changed = plan(planId).fail()
        if (!changed) return illegal("fail order plan")
        failureReason = reason
        status = TradeStatus.FAILED
        touch()
        return Success(true)
    }

    fun recordOrderCancelled(
        planId: TradeOrderPlanId,
        orderId: Long,
        reason: String,
    ): Result<Boolean, BusinessError> {
        if (reason.isBlank() || orderId <= 0) return Failure(TradeErrors.INVALID_REASON)
        val cancelledPlan = plan(planId)
        if (cancelledPlan.orderId != orderId) return Failure(TradeErrors.ORDER_MISMATCH)
        if (status == TradeStatus.FAILED) return Success(false)
        if (status !in setOf(TradeStatus.CREATING_ORDERS, TradeStatus.SETTLEMENT_PREPARING)) {
            return illegal("record cancelled order")
        }
        orderPlans.filter { it.orderId != null }.forEach { it.closeCreatedOrder() }
        failureReason = reason
        status = TradeStatus.FAILED
        touch()
        return Success(true)
    }

    fun matchesRequest(buyerParty: BuyerPartySnapshot, digest: String): Boolean =
        this.buyerParty == buyerParty && requestDigest == digest

    private fun touch() {
        updatedAt = Instant.now()
    }

    private fun illegal(action: String): Failure<BusinessError> =
        Failure(TradeErrors.ILLEGAL_STATE.msg("Cannot $action while trade is $status"))

    companion object {
        fun start(
            id: TradeId,
            checkoutRequestId: String,
            requestDigest: String,
            buyerParty: BuyerPartySnapshot,
            buyerProfile: TradeBuyerProfileSnapshot,
            actingPrincipalId: Long,
            recipient: TradeRecipientSnapshot,
            orderPlans: List<TradeOrderPlan>,
            currency: String,
            commitmentPolicy: CommitmentPolicySnapshot,
            settlementTerms: SettlementTermsSnapshot,
        ) =
            Trade(
                id,
                checkoutRequestId,
                requestDigest,
                buyerParty,
                buyerProfile,
                actingPrincipalId,
                recipient,
                orderPlans,
                Price.sumOf(orderPlans.map { it.payableAmount }),
                currency,
                commitmentPolicy,
                settlementTerms,
            )
    }
}
