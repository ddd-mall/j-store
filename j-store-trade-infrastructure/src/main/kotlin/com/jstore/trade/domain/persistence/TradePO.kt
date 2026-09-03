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
package com.jstore.trade.domain.persistence

import com.jstore.common.geo.I18nGeoAddress
import com.jstore.trade.domain.*
import jakarta.persistence.*
import java.time.Instant
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Embeddable
class TradeItemPO(
    @Column(name = "offer_id", nullable = false) var offerId: Long = 0,
    @Column(name = "store_id", nullable = false) var storeId: Long = 0,
    @Column(name = "spu_id", nullable = false) var spuId: Long = 0,
    @Column(name = "sku_id", nullable = false) var skuId: Long = 0,
    @Column(nullable = false) var quantity: Int = 0,
    @Column(name = "catalog_snapshot_version", nullable = false)
    var catalogSnapshotVersion: Long = 0,
    @Column(name = "offer_version", nullable = false) var offerVersion: Long = 0,
    @Column(name = "fulfillment_node_id", nullable = false, length = 128)
    var fulfillmentNodeId: String = "",
    @Column(name = "channel_id", nullable = false, length = 64) var channelId: String = "",
    @Column(name = "unit_price_fen", nullable = false) var unitPriceFen: Long = 0,
    @Column(name = "goods_name", nullable = false, length = 256) var goodsName: String = "",
    @Column(name = "sku_description", nullable = false, length = 512)
    var skuDescription: String = "",
)

@Embeddable
class TradeAuthorizationPO(
    @Column(name = "authorization_id", nullable = false, length = 128)
    var authorizationId: String = "",
    @Column(name = "offer_id", nullable = false) var offerId: Long = 0,
    @Column(name = "expires_at", nullable = false) var expiresAt: Instant = Instant.EPOCH,
)

@Embeddable
class TradeReservationPO(
    @Column(name = "reservation_id", nullable = false, length = 192) var reservationId: String = ""
)

@Embeddable
class TradeCustomsFieldPO(
    @Column(name = "field_name", nullable = false, length = 128) var name: String = "",
    @Column(name = "field_value", nullable = false, length = 1024) var value: String = "",
)

@Embeddable
class TradeInstallmentPO(
    @Column(name = "installment_id", nullable = false, length = 128) var installmentId: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    var purpose: InstallmentPurpose = InstallmentPurpose.FULL,
    @Column(name = "amount_fen", nullable = false) var amountFen: Long = 0,
)

@Embeddable
class TradePaymentReferencePO(
    @Column(name = "installment_id", nullable = false, length = 128) var installmentId: String = "",
    @Column(name = "payment_id", nullable = false) var paymentId: Long = 0,
)

@Entity
@Table(name = "trade_order_plans")
class TradeOrderPlanPO(
    @Id @Column(name = "order_plan_id") var id: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "fulfillment_group", nullable = false, length = 128)
    var fulfillmentGroup: String = "",
    @Column(name = "payable_amount_fen", nullable = false) var payableAmountFen: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: TradeOrderPlanStatus = TradeOrderPlanStatus.AUTHORIZING,
    @Column(name = "order_id", unique = true) var orderId: Long? = null,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trade_order_plan_items",
        joinColumns = [JoinColumn(name = "order_plan_id")],
    )
    @OrderColumn(name = "line_no")
    var items: MutableList<TradeItemPO> = mutableListOf(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trade_order_plan_authorizations",
        joinColumns = [JoinColumn(name = "order_plan_id")],
    )
    @OrderColumn(name = "line_no")
    var authorizations: MutableList<TradeAuthorizationPO> = mutableListOf(),
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trade_order_plan_reservations",
        joinColumns = [JoinColumn(name = "order_plan_id")],
    )
    @OrderColumn(name = "line_no")
    var reservations: MutableList<TradeReservationPO> = mutableListOf(),
    @Column(name = "reservation_expires_at") var reservationExpiresAt: Instant? = null,
)

@Entity
@Table(
    name = "trades",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_trade_buyer_checkout_request",
                columnNames =
                    [
                        "acting_principal_authentication_domain",
                        "acting_principal_id",
                        "checkout_request_id",
                    ],
            )
        ],
)
class TradePO(
    @Id var id: Long = 0,
    @Column(name = "checkout_request_id", nullable = false, length = 128)
    var checkoutRequestId: String = "",
    @Column(name = "request_digest", nullable = false, length = 80) var requestDigest: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "checkout_source_type", nullable = false, length = 16)
    var checkoutSourceType: CheckoutSourceType = CheckoutSourceType.DIRECT,
    @Column(name = "checkout_source_id") var checkoutSourceId: Long? = null,
    @Column(name = "checkout_source_version") var checkoutSourceVersion: Long? = null,
    @Column(name = "checkout_source_digest", nullable = false, length = 128)
    var checkoutSourceDigest: String = "DIRECT",
    @Enumerated(EnumType.STRING)
    @Column(name = "buyer_party_type", nullable = false, length = 32)
    var buyerPartyType: PartyType = PartyType.INDIVIDUAL,
    @Column(name = "buyer_party_id", nullable = false) var buyerPartyId: Long = 0,
    @Column(name = "buyer_display_name", nullable = false, length = 128)
    var buyerDisplayName: String = "",
    @Column(name = "buyer_phone", length = 32) var buyerPhone: String? = null,
    @Column(name = "acting_principal_id", nullable = false) var actingPrincipalId: Long = 0,
    @Column(name = "acting_principal_authentication_domain", nullable = false, length = 255)
    var actingPrincipalAuthenticationDomain: String = "",
    @Column(name = "recipient_name", nullable = false, length = 256) var recipientName: String = "",
    @Column(name = "country_code", nullable = false, length = 8) var countryCode: String = "",
    @Column(name = "recipient_phone", length = 64) var recipientPhone: String? = null,
    @Column(name = "recipient_email", length = 320) var recipientEmail: String? = null,
    @Column(name = "district_code", nullable = false, length = 64) var districtCode: String = "",
    @Column(name = "detail_address", nullable = false, length = 1024)
    var detailAddress: String = "",
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "shipping_address", nullable = false, columnDefinition = "jsonb")
    var shippingAddress: I18nGeoAddress? = null,
    @Column(name = "postal_code", length = 32) var postalCode: String? = null,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "trade_customs_fields", joinColumns = [JoinColumn(name = "trade_id")])
    var customsFields: MutableList<TradeCustomsFieldPO> = mutableListOf(),
    @Column(name = "payable_amount_fen", nullable = false) var payableAmountFen: Long = 0,
    @Column(nullable = false, length = 3) var currency: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "trade_mode", nullable = false, length = 32)
    var tradeMode: TradeMode = TradeMode.NORMAL,
    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_mode", nullable = false, length = 32)
    var settlementMode: SettlementMode = SettlementMode.PREPAID,
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_release_rule", nullable = false, length = 32)
    var fulfillmentReleaseRule: FulfillmentReleaseRule = FulfillmentReleaseRule.FULL_PAYMENT,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trade_payment_installments",
        joinColumns = [JoinColumn(name = "trade_id")],
    )
    @OrderColumn(name = "sequence_no")
    var installments: MutableList<TradeInstallmentPO> = mutableListOf(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false)
    @OrderColumn(name = "plan_no")
    var orderPlans: MutableList<TradeOrderPlanPO> = mutableListOf(),
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: TradeStatus = TradeStatus.AUTHORIZING,
    @Column(name = "settlement_plan_id", unique = true) var settlementPlanId: Long? = null,
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "trade_installment_payment_refs",
        joinColumns = [JoinColumn(name = "trade_id")],
    )
    var paymentReferences: MutableList<TradePaymentReferencePO> = mutableListOf(),
    @Column(name = "failure_reason", length = 1024) var failureReason: String? = null,
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.EPOCH,
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.EPOCH,
    @Version var persistenceVersion: Long = 0,
)
