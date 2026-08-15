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
    val items: List<CheckoutItem>,
)

data class CheckoutAccepted(val tradeId: Long, val orderIds: List<Long>)

data class CheckoutView(val tradeId: Long, val status: String, val orderIds: List<Long>)

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

interface CheckoutUseCase {
    fun checkout(command: CreateCheckoutCommand): Result<CheckoutAccepted, BusinessError>

    fun find(buyerId: Long, tradeId: Long): Result<CheckoutView, BusinessError>
}

class CheckoutApplicationService(
    private val preparation: CheckoutPreparationGateway,
    private val trades: TradeRepository,
    private val ids: TradeIdentityGenerator,
    private val authorization: TradeAuthorizationGateway,
) : CheckoutUseCase {
    override fun checkout(command: CreateCheckoutCommand): Result<CheckoutAccepted, BusinessError> {
        if (!command.isValid()) return Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        val buyer = BuyerPartySnapshot(PartyType.INDIVIDUAL, command.buyerId)
        val digest = digest(command)
        trades.findByCheckoutRequest(buyer, command.checkoutRequestId)?.let { existing ->
            return if (existing.matchesRequest(buyer, digest)) Success(existing.accepted())
            else Failure(TradeErrors.START_CONFLICT)
        }

        val prepared =
            when (val result = preparation.prepare(command)) {
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
                checkoutRequestId = command.checkoutRequestId,
                requestDigest = digest,
                buyerParty = buyer,
                buyerProfile = prepared.buyerProfile,
                actingPrincipalId = command.buyerId,
                recipient = command.recipient.toSnapshot(prepared.shippingAddress),
                orderPlans = plans,
                currency = prepared.currency,
                commitmentPolicy = CommitmentPolicySnapshot(TradeMode.NORMAL),
                settlementTerms =
                    SettlementTermsSnapshot(
                        SettlementMode.PREPAID,
                        FulfillmentReleaseRule.FULL_PAYMENT,
                        listOf(PaymentInstallmentSnapshot("FULL", InstallmentPurpose.FULL, total)),
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
        return Success(
            CheckoutView(
                trade.id.value,
                trade.status.name,
                trade.orderPlans.mapNotNull { it.orderId },
            )
        )
    }

    private fun CreateCheckoutCommand.isValid() =
        checkoutRequestId.isNotBlank() &&
            buyerId > 0 &&
            recipient.name.isNotBlank() &&
            recipient.countryCode.isNotBlank() &&
            recipient.districtCode.isNotBlank() &&
            recipient.detailAddress.isNotBlank() &&
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
            append("v1|").append(command.buyerId).append('|')
            append(command.recipient.name.trim()).append('|')
            append(command.recipient.countryCode.uppercase()).append('|')
            append(command.recipient.phone?.trim().orEmpty()).append('|')
            append(command.recipient.email?.trim()?.lowercase().orEmpty()).append('|')
            append(command.recipient.districtCode.trim()).append('|')
            append(command.recipient.detailAddress.trim()).append('|')
            append(command.recipient.postalCode?.trim().orEmpty()).append('|')
            command.recipient.customsFields.toSortedMap().forEach { (key, value) ->
                append(key.trim()).append('=').append(value.trim()).append(';')
            }
            append('|')
            command.items
                .sortedBy { it.offerId }
                .forEach {
                    append(it.offerId).append(':').append(it.offerVersion).append(':')
                    append(it.spuId).append(':').append(it.skuId).append(':')
                    append(it.quantity).append(':').append(it.catalogSnapshotVersion).append(';')
                }
        }
        return "v1:" +
            MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") {
                "%02x".format(it)
            }
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
}
