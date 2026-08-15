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
package com.jstore.trade.config

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.PreparePaymentInstallmentCommand
import com.jstore.messaging.IntegrationMessage
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.*
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradeSettlementMessageGatewayTest {
    private val now = Instant.parse("2030-01-01T00:00:00Z")

    @Test
    fun `payment preparation uses a bounded action window before reservation safety margin`() {
        val publisher = CapturingIntegrationPublisher()
        val gateway = gateway(publisher)

        assertIs<Success<Unit>>(
            gateway.prepareSettlement(trade(now.plus(Duration.ofMinutes(20))), SettlementPlanId(91))
        )

        val command = assertIs<PreparePaymentInstallmentCommand>(publisher.messages.single())
        assertEquals(now.plus(Duration.ofMinutes(1)), command.acceptBefore)
        assertEquals(now.plus(Duration.ofMinutes(15)), command.expiresAt)
    }

    @Test
    fun `payment preparation rejects reservation window shorter than action plus safety margin`() {
        val publisher = CapturingIntegrationPublisher()
        val gateway = gateway(publisher)

        val result =
            gateway.prepareSettlement(trade(now.plus(Duration.ofMinutes(16))), SettlementPlanId(91))

        assertEquals(
            TradeErrors.RESERVATION_WINDOW_INSUFFICIENT.errorCode,
            assertIs<Failure<*>>(result)
                .error
                .let { it as com.jstore.common.errors.BusinessError }
                .errorCode,
        )
        assertEquals(emptyList(), publisher.messages)
    }

    private fun gateway(publisher: IntegrationMessagePublisher) =
        TradeSettlementMessageGateway(
            publisher,
            { now },
            preparationTimeout = Duration.ofMinutes(1),
            paymentActionLifetime = Duration.ofMinutes(15),
            safetyMargin = Duration.ofMinutes(2),
        )

    private fun trade(reservationExpiresAt: Instant): Trade {
        val plan =
            TradeOrderPlan(
                TradeOrderPlanId(11),
                7,
                "NODE-1",
                listOf(
                    TradeItemSnapshot(
                        21,
                        31,
                        41,
                        51,
                        1,
                        1,
                        1,
                        "NODE-1",
                        "WEB",
                        Price.ofFen(1000),
                        "商品",
                        "规格",
                    )
                ),
                Price.ofFen(1000),
                TradeOrderPlanStatus.ORDER_CREATED,
                listOf(TradeAuthorization("A-1", 21, reservationExpiresAt)),
                listOf("R-1"),
                reservationExpiresAt,
                81,
            )
        return Trade(
            TradeId(1),
            "checkout-1",
            "digest",
            BuyerPartySnapshot(PartyType.INDIVIDUAL, 42),
            TradeBuyerProfileSnapshot("买家", null),
            42,
            TradeRecipientSnapshot(
                "收货人",
                "CN",
                "13800000000",
                null,
                "110105",
                "示例路 1 号",
                testAddress(),
            ),
            listOf(plan),
            Price.ofFen(1000),
            "CNY",
            CommitmentPolicySnapshot(TradeMode.NORMAL),
            SettlementTermsSnapshot(
                SettlementMode.PREPAID,
                FulfillmentReleaseRule.FULL_PAYMENT,
                listOf(
                    PaymentInstallmentSnapshot("FULL", InstallmentPurpose.FULL, Price.ofFen(1000))
                ),
            ),
            TradeStatus.SETTLEMENT_PREPARING,
            SettlementPlanId(91),
        )
    }

    private fun testAddress() =
        I18nGeoAddress(
            CountryCode.CN,
            listOf(
                AddressComponent(
                    "110105",
                    DivisionLevel(3, "district"),
                    mapOf(Locale.CHINA to "朝阳区"),
                    Locale.CHINA,
                )
            ),
        )
}

private class CapturingIntegrationPublisher : IntegrationMessagePublisher {
    val messages = mutableListOf<IntegrationMessage>()

    override fun publish(message: IntegrationMessage) {
        messages += message
    }
}
