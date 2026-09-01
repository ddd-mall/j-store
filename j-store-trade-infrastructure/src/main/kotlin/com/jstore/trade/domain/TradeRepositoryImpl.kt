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

import com.jstore.common.properties.Price
import com.jstore.trade.domain.persistence.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class TradeRepositoryImpl(private val jpa: TradePOJpaRepository) : TradeRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: Trade): Trade = toDomain(jpa.save(toPO(aggregate)))

    override fun findById(id: TradeId): Trade? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByCheckoutRequest(
        buyerParty: BuyerPartySnapshot,
        checkoutRequestId: String,
    ): Trade? =
        jpa.findByBuyerPartyTypeAndBuyerPartyIdAndCheckoutRequestId(
                buyerParty.partyType,
                buyerParty.partyId,
                checkoutRequestId,
            )
            ?.let(::toDomain)

    override fun findByOrderPlanId(orderPlanId: TradeOrderPlanId): Trade? =
        jpa.findByOrderPlansId(orderPlanId.value)?.let(::toDomain)

    private fun toPO(trade: Trade) =
        TradePO(
            id = trade.id.value,
            checkoutRequestId = trade.checkoutRequestId,
            requestDigest = trade.requestDigest,
            checkoutSourceType = trade.sourceSnapshot.type,
            checkoutSourceId = trade.sourceSnapshot.sourceId,
            checkoutSourceVersion = trade.sourceSnapshot.sourceVersion,
            checkoutSourceDigest = trade.sourceSnapshot.sourceDigest,
            buyerPartyType = trade.buyerParty.partyType,
            buyerPartyId = trade.buyerParty.partyId,
            buyerDisplayName = trade.buyerProfile.displayName,
            buyerPhone = trade.buyerProfile.phone,
            actingPrincipalId = trade.actingPrincipalId,
            recipientName = trade.recipient.name,
            countryCode = trade.recipient.countryCode,
            recipientPhone = trade.recipient.phone,
            recipientEmail = trade.recipient.email,
            districtCode = trade.recipient.districtCode,
            detailAddress = trade.recipient.detailAddress,
            shippingAddress = trade.recipient.shippingAddress,
            postalCode = trade.recipient.postalCode,
            customsFields =
                trade.recipient.customsFields
                    .toSortedMap()
                    .map { TradeCustomsFieldPO(it.key, it.value) }
                    .toMutableList(),
            payableAmountFen = trade.payableAmount.fen,
            currency = trade.currency,
            tradeMode = trade.commitmentPolicy.tradeMode,
            settlementMode = trade.settlementTerms.mode,
            fulfillmentReleaseRule = trade.settlementTerms.fulfillmentReleaseRule,
            installments =
                trade.settlementTerms.installments
                    .map { TradeInstallmentPO(it.installmentId, it.purpose, it.amount.fen) }
                    .toMutableList(),
            orderPlans = trade.orderPlans.map(::toPlanPO).toMutableList(),
            status = trade.status,
            settlementPlanId = trade.settlementPlanId?.value,
            paymentReferences =
                trade.paymentReferences
                    .toSortedMap()
                    .map { TradePaymentReferencePO(it.key, it.value) }
                    .toMutableList(),
            failureReason = trade.failureReason,
            createdAt = trade.createdAt,
            updatedAt = trade.updatedAt,
            persistenceVersion = trade.persistenceVersion,
        )

    private fun toPlanPO(plan: TradeOrderPlan) =
        TradeOrderPlanPO(
            id = plan.id.value,
            merchantId = plan.merchantId,
            fulfillmentGroup = plan.fulfillmentGroup,
            payableAmountFen = plan.payableAmount.fen,
            status = plan.status,
            orderId = plan.orderId,
            items =
                plan.items
                    .map {
                        TradeItemPO(
                            it.offerId,
                            it.storeId,
                            it.spuId,
                            it.skuId,
                            it.quantity,
                            it.catalogSnapshotVersion,
                            it.offerVersion,
                            it.fulfillmentNodeId,
                            it.channelId,
                            it.unitPrice.fen,
                            it.goodsName,
                            it.skuDescription,
                        )
                    }
                    .toMutableList(),
            authorizations =
                plan.authorizations
                    .map { TradeAuthorizationPO(it.authorizationId, it.offerId, it.expiresAt) }
                    .toMutableList(),
            reservations = plan.reservationIds.map(::TradeReservationPO).toMutableList(),
            reservationExpiresAt = plan.reservationExpiresAt,
        )

    private fun toDomain(po: TradePO) =
        Trade(
            id = TradeId(po.id),
            checkoutRequestId = po.checkoutRequestId,
            requestDigest = po.requestDigest,
            buyerParty = BuyerPartySnapshot(po.buyerPartyType, po.buyerPartyId),
            buyerProfile = TradeBuyerProfileSnapshot(po.buyerDisplayName, po.buyerPhone),
            actingPrincipalId = po.actingPrincipalId,
            recipient =
                TradeRecipientSnapshot(
                    po.recipientName,
                    po.countryCode,
                    po.recipientPhone,
                    po.recipientEmail,
                    po.districtCode,
                    po.detailAddress,
                    requireNotNull(po.shippingAddress),
                    po.postalCode,
                    po.customsFields.associate { it.name to it.value },
                ),
            orderPlans = po.orderPlans.map(::toPlan),
            payableAmount = Price.ofFen(po.payableAmountFen),
            currency = po.currency,
            commitmentPolicy = CommitmentPolicySnapshot(po.tradeMode),
            settlementTerms =
                SettlementTermsSnapshot(
                    po.settlementMode,
                    po.fulfillmentReleaseRule,
                    po.installments.map {
                        PaymentInstallmentSnapshot(
                            it.installmentId,
                            it.purpose,
                            Price.ofFen(it.amountFen),
                        )
                    },
                ),
            status = po.status,
            settlementPlanId = po.settlementPlanId?.let(::SettlementPlanId),
            paymentReferences = po.paymentReferences.associate { it.installmentId to it.paymentId },
            failureReason = po.failureReason,
            createdAt = po.createdAt,
            updatedAt = po.updatedAt,
            persistenceVersion = po.persistenceVersion,
            sourceSnapshot = CheckoutSourceSnapshot(po.checkoutSourceType, po.checkoutSourceId, po.checkoutSourceVersion, po.checkoutSourceDigest),
        )

    private fun toPlan(po: TradeOrderPlanPO) =
        TradeOrderPlan(
            id = TradeOrderPlanId(po.id),
            merchantId = po.merchantId,
            fulfillmentGroup = po.fulfillmentGroup,
            items =
                po.items.map {
                    TradeItemSnapshot(
                        it.offerId,
                        it.storeId,
                        it.spuId,
                        it.skuId,
                        it.quantity,
                        it.catalogSnapshotVersion,
                        it.offerVersion,
                        it.fulfillmentNodeId,
                        it.channelId,
                        Price.ofFen(it.unitPriceFen),
                        it.goodsName,
                        it.skuDescription,
                    )
                },
            payableAmount = Price.ofFen(po.payableAmountFen),
            status = po.status,
            authorizations =
                po.authorizations.map {
                    TradeAuthorization(it.authorizationId, it.offerId, it.expiresAt)
                },
            reservationIds = po.reservations.map { it.reservationId },
            reservationExpiresAt = po.reservationExpiresAt,
            orderId = po.orderId,
        )
}
