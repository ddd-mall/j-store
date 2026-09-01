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
package com.jstore.trade.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.trade.domain.*
import java.security.MessageDigest
import java.time.Instant

data class CheckoutItem(
    val offerId: Long,
    val offerVersion: Long,
    val spuId: Long,
    val skuId: Long,
    val quantity: Int,
    val catalogSnapshotVersion: Long,
)

data class CheckoutRecipient(
    val name: String,
    val countryCode: String,
    val phone: String?,
    val email: String?,
    val districtCode: String,
    val detailAddress: String,
    val postalCode: String? = null,
    val customsFields: Map<String, String> = emptyMap(),
)

data class CreateCheckoutCommand(
    val checkoutRequestId: String,
    val buyerId: Long,
    val recipient: CheckoutRecipient,
    val items: List<CheckoutItem> = emptyList(),
    val cartId: Long? = null,
    val expectedCartVersion: Long? = null,
    val cartDigest: String? = null,
)

fun interface CheckoutSourceGateway {
    fun resolve(command: CreateCheckoutCommand): Result<CreateCheckoutCommand, BusinessError>
}

data class CheckoutAccepted(val tradeId: Long, val orderIds: List<Long>)

data class CheckoutPaymentView(
    val paymentId: Long,
    val status: String,
    val amountFen: Long,
    val currency: String,
    val payAction: String,
    val expiresAt: Instant,
)

data class CheckoutView(
    val tradeId: Long,
    val status: String,
    val orderIds: List<Long>,
    val payment: CheckoutPaymentView? = null,
)

data class PreparedCheckoutItem(
    val offerId: Long,
    val storeId: Long,
    val merchantId: Long,
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
)

data class PreparedCheckout(
    val command: CreateCheckoutCommand,
    val items: List<PreparedCheckoutItem>,
    val buyerProfile: TradeBuyerProfileSnapshot,
    val shippingAddress: I18nGeoAddress,
    val currency: String = "CNY",
)

fun interface CheckoutPreparationGateway {
    fun prepare(command: CreateCheckoutCommand): Result<PreparedCheckout, BusinessError>
}

fun interface TradeIdentityGenerator {
    fun nextId(): Long
}

fun interface TradeAuthorizationGateway {
    fun requestAuthorization(trade: Trade, plan: TradeOrderPlan)
}

fun interface CheckoutPaymentGateway {
    fun findReadyPayment(paymentId: Long): CheckoutPaymentView?
}

interface CheckoutUseCase {
    fun checkout(command: CreateCheckoutCommand): Result<CheckoutAccepted, BusinessError>

    fun find(buyerId: Long, tradeId: Long): Result<CheckoutView, BusinessError>
}

class CheckoutApplicationService(
    private val preparation: CheckoutPreparationGateway,
    private val trades: TradeRepository,
    private val ids: TradeIdentityGenerator,
    private val authorization: TradeAuthorizationGateway,
    private val payments: CheckoutPaymentGateway = CheckoutPaymentGateway { null },
    private val source: CheckoutSourceGateway = CheckoutSourceGateway { command ->
        if (command.cartId == null) Success(command)
        else Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
    },
) : CheckoutUseCase {
    override fun checkout(command: CreateCheckoutCommand): Result<CheckoutAccepted, BusinessError> {
        val requestedBuyer = BuyerPartySnapshot(PartyType.INDIVIDUAL, command.buyerId)
        if (command.cartId != null) {
            trades.findByCheckoutRequest(requestedBuyer, command.checkoutRequestId)?.let { existing
                ->
                return if (existing.matchesCartRetry(command)) Success(existing.accepted())
                else Failure(TradeErrors.START_CONFLICT)
            }
        }
        val resolved =
            when (val result = source.resolve(command)) {
                is Failure -> return result
                is Success -> result.value
            }
        if (!resolved.isValid()) return Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        val buyer = BuyerPartySnapshot(PartyType.INDIVIDUAL, resolved.buyerId)
        val digest = digest(resolved)
        trades.findByCheckoutRequest(buyer, resolved.checkoutRequestId)?.let { existing ->
            return if (existing.matchesRequest(buyer, digest)) Success(existing.accepted())
            else Failure(TradeErrors.START_CONFLICT)
        }

        val prepared =
            when (val result = preparation.prepare(resolved)) {
                is Failure -> return result
                is Success -> result.value
            }
        if (prepared.items.isEmpty()) return Failure(TradeErrors.CHECKOUT_OFFER_INVALID)

        val plans =
            prepared.items
                .groupBy { it.merchantId to it.fulfillmentNodeId }
                .toSortedMap(compareBy<Pair<Long, String>>({ it.first }, { it.second }))
                .map { (group, items) ->
                    val snapshots = items.sortedBy { it.offerId }.map { it.toSnapshot() }
                    TradeOrderPlan(
                        id = TradeOrderPlanId(ids.nextId()),
                        merchantId = group.first,
                        fulfillmentGroup = group.second,
                        items = snapshots,
                        payableAmount = Price.sumOf(snapshots.map { it.unitPrice * it.quantity }),
                    )
                }
        val total = Price.sumOf(plans.map { it.payableAmount })
        val trade =
            Trade.start(
                id = TradeId(ids.nextId()),
                checkoutRequestId = resolved.checkoutRequestId,
                requestDigest = digest,
                buyerParty = buyer,
                buyerProfile = prepared.buyerProfile,
                actingPrincipalId = resolved.buyerId,
                recipient = resolved.recipient.toSnapshot(prepared.shippingAddress),
                orderPlans = plans,
                currency = prepared.currency,
                commitmentPolicy = CommitmentPolicySnapshot(TradeMode.NORMAL),
                settlementTerms =
                    SettlementTermsSnapshot(
                        SettlementMode.PREPAID,
                        FulfillmentReleaseRule.FULL_PAYMENT,
                        listOf(PaymentInstallmentSnapshot("FULL", InstallmentPurpose.FULL, total)),
                    ),
                sourceSnapshot =
                    if (resolved.cartId == null) CheckoutSourceSnapshot.direct(digest)
                    else
                        CheckoutSourceSnapshot.cart(
                            resolved.cartId,
                            requireNotNull(resolved.expectedCartVersion),
                            requireNotNull(resolved.cartDigest),
                        ),
            )
        trades.save(trade)
        trade.orderPlans.forEach { authorization.requestAuthorization(trade, it) }
        return Success(trade.accepted())
    }

    override fun find(buyerId: Long, tradeId: Long): Result<CheckoutView, BusinessError> {
        val trade = trades.findById(TradeId(tradeId)) ?: return Failure(TradeErrors.NOT_FOUND)
        if (trade.buyerParty != BuyerPartySnapshot(PartyType.INDIVIDUAL, buyerId)) {
            return Failure(TradeErrors.NOT_FOUND)
        }
        val payment =
            if (trade.status == TradeStatus.PAYMENT_READY) {
                val installment = trade.settlementTerms.installments.single()
                val paymentId =
                    trade.paymentIdFor(installment.installmentId)
                        ?: return Failure(TradeErrors.PAYMENT_UNAVAILABLE)
                val candidate = payments.findReadyPayment(paymentId)
                if (candidate == null) return Success(trade.toView(null))
                if (
                    candidate.paymentId != paymentId ||
                        candidate.status != "READY" ||
                        candidate.amountFen != installment.amount.fen ||
                        candidate.currency != trade.currency
                ) {
                    return Failure(TradeErrors.PAYMENT_UNAVAILABLE)
                }
                candidate
            } else {
                null
            }
        return Success(trade.toView(payment))
    }

    /** Read-only recovery after the database resolves a concurrent checkout-key insert race. */
    fun recoverConcurrentCheckout(
        command: CreateCheckoutCommand
    ): Result<CheckoutAccepted, BusinessError>? {
        if (command.cartId != null) {
            val buyer = BuyerPartySnapshot(PartyType.INDIVIDUAL, command.buyerId)
            val existing =
                trades.findByCheckoutRequest(buyer, command.checkoutRequestId) ?: return null
            return if (existing.matchesCartRetry(command)) Success(existing.accepted())
            else Failure(TradeErrors.START_CONFLICT)
        }
        val resolved =
            when (val result = source.resolve(command)) {
                is Failure -> return result
                is Success -> result.value
            }
        if (!resolved.isValid()) return Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        val buyer = BuyerPartySnapshot(PartyType.INDIVIDUAL, resolved.buyerId)
        val existing =
            trades.findByCheckoutRequest(buyer, resolved.checkoutRequestId) ?: return null
        return if (existing.matchesRequest(buyer, digest(resolved))) Success(existing.accepted())
        else Failure(TradeErrors.START_CONFLICT)
    }

    private fun CreateCheckoutCommand.isValid() =
        checkoutRequestId.isNotBlank() &&
            checkoutRequestId.length <= 128 &&
            buyerId > 0 &&
            recipient.name.isNotBlank() &&
            recipient.name.length <= 256 &&
            recipient.countryCode.isNotBlank() &&
            recipient.countryCode.length <= 8 &&
            (recipient.phone == null || recipient.phone.length <= 64) &&
            (recipient.email == null || recipient.email.length <= 320) &&
            recipient.districtCode.isNotBlank() &&
            recipient.districtCode.length <= 64 &&
            recipient.detailAddress.isNotBlank() &&
            recipient.detailAddress.length <= 1024 &&
            (recipient.postalCode == null || recipient.postalCode.length <= 32) &&
            recipient.customsFields.all { (key, value) ->
                key.isNotBlank() && key.length <= 128 && value.length <= 1024
            } &&
            (!recipient.phone.isNullOrBlank() || !recipient.email.isNullOrBlank()) &&
            items.isNotEmpty() &&
            items.map { it.offerId }.distinct().size == items.size &&
            items.all {
                it.offerId > 0 &&
                    it.offerVersion > 0 &&
                    it.spuId > 0 &&
                    it.skuId > 0 &&
                    it.quantity > 0 &&
                    it.catalogSnapshotVersion > 0
            }

    private fun digest(command: CreateCheckoutCommand): String {
        val canonical = buildString {
            appendCanonical("v2")
            appendCanonical(command.buyerId.toString())
            appendCanonical(command.recipient.name)
            appendCanonical(command.recipient.countryCode)
            appendCanonical(command.recipient.phone)
            appendCanonical(command.recipient.email)
            appendCanonical(command.recipient.districtCode)
            appendCanonical(command.recipient.detailAddress)
            appendCanonical(command.recipient.postalCode)
            appendCanonical(command.recipient.customsFields.size.toString())
            command.recipient.customsFields.toSortedMap().forEach { (key, value) ->
                appendCanonical(key)
                appendCanonical(value)
            }
            appendCanonical(command.items.size.toString())
            appendCanonical(command.cartId?.toString())
            appendCanonical(command.expectedCartVersion?.toString())
            appendCanonical(command.cartDigest)
            command.items
                .sortedBy { it.offerId }
                .forEach {
                    appendCanonical(it.offerId.toString())
                    appendCanonical(it.offerVersion.toString())
                    appendCanonical(it.spuId.toString())
                    appendCanonical(it.skuId.toString())
                    appendCanonical(it.quantity.toString())
                    appendCanonical(it.catalogSnapshotVersion.toString())
                }
        }
        return "v2:" +
            MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
    }

    private fun StringBuilder.appendCanonical(value: String?) {
        if (value == null) append("-1:") else append(value.length).append(':').append(value)
    }

    private fun PreparedCheckoutItem.toSnapshot() =
        TradeItemSnapshot(
            offerId,
            storeId,
            spuId,
            skuId,
            quantity,
            catalogSnapshotVersion,
            offerVersion,
            fulfillmentNodeId,
            channelId,
            unitPrice,
            goodsName,
            skuDescription,
        )

    private fun CheckoutRecipient.toSnapshot(shippingAddress: I18nGeoAddress) =
        TradeRecipientSnapshot(
            name,
            countryCode,
            phone,
            email,
            districtCode,
            detailAddress,
            shippingAddress,
            postalCode,
            customsFields,
        )

    private fun Trade.accepted() = CheckoutAccepted(id.value, orderPlans.mapNotNull { it.orderId })

    private fun Trade.toView(payment: CheckoutPaymentView?) =
        CheckoutView(id.value, status.name, orderPlans.mapNotNull { it.orderId }, payment)

    private fun Trade.matchesCartRetry(command: CreateCheckoutCommand): Boolean =
        buyerParty == BuyerPartySnapshot(PartyType.INDIVIDUAL, command.buyerId) &&
            sourceSnapshot.type == CheckoutSourceType.CART &&
            sourceSnapshot.sourceId == command.cartId &&
            sourceSnapshot.sourceVersion == command.expectedCartVersion &&
            recipient.name == command.recipient.name &&
            recipient.countryCode == command.recipient.countryCode &&
            recipient.phone == command.recipient.phone &&
            recipient.email == command.recipient.email &&
            recipient.districtCode == command.recipient.districtCode &&
            recipient.detailAddress == command.recipient.detailAddress &&
            recipient.postalCode == command.recipient.postalCode &&
            recipient.customsFields == command.recipient.customsFields
}
