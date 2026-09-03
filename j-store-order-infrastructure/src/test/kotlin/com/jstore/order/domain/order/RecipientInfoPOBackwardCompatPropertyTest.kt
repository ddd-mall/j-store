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
package com.jstore.order.domain.order

import com.jstore.common.geo.*
import com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.order.domain.order.persistence.RecipientInfoPO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

/**
 * Feature: order-consignee-info, Property 5: 历史数据反序列化默认值
 *
 * For any 合法的 RecipientInfoPO JSON，当 consigneeName 字段缺失或为 null 时， 反序列化后重建的
 * ShippingInfo.consigneeName 应为空字符串 ""； 当 consigneePhone 和 consigneeEmail 均缺失或为 null 时， 重建的
 * ContractInfo 的 phoneNumber 和 email 均应为 null。
 *
 * **Validates: Requirements 6.1, 6.2**
 */
class RecipientInfoPOBackwardCompatPropertyTest :
    FunSpec({

        // Generator for non-blank strings
        val nonBlankStringArb: Arb<String> = Arb.string(1..30).filter { it.isNotBlank() }

        // Generator for 6-digit code strings
        val digitCodeArb: Arb<String> = Arb.int(100000..999999).map { it.toString() }

        // Generator for I18nGeoAddress (always valid — historical data always has an address)
        val i18nGeoAddressArb: Arb<I18nGeoAddress> =
            Arb.bind(
                digitCodeArb,
                nonBlankStringArb,
            ) { code, name ->
                I18nGeoAddress(
                    countryCode = CountryCode.CN,
                    components =
                        listOf(
                            AddressComponent(
                                code = code,
                                level = DivisionLevel(1, "省"),
                                names = mapOf(Locale.SIMPLIFIED_CHINESE to name),
                                defaultLocale = Locale.SIMPLIFIED_CHINESE,
                            )
                        ),
                )
            }

        // Generator for optional detail address
        val optionalDetailAddressArb: Arb<String?> =
            Arb.choice(
                Arb.constant(null as String?),
                nonBlankStringArb,
            )

        val converter = OrderRepositoryImpl.Converter
        val now = LocalDateTime.now()

        test("consigneeName null defaults to empty string in ShippingInfo") {
            checkAll(100, i18nGeoAddressArb, digitCodeArb, optionalDetailAddressArb) {
                address,
                districtCode,
                detailAddress ->
                // Simulate historical data: consigneeName is null
                val historicalPO =
                    RecipientInfoPO(
                        consigneeName = null,
                        consigneePhone = "+8613800138000",
                        consigneeEmail = "test@test.com",
                        countryCode = address.countryCode.value,
                        districtCode = districtCode,
                        shippingAddress = address,
                        detailAddress = detailAddress,
                    )

                val orderPO =
                    OrderPO(
                        id = 1L,
                        merchantId = 1L,
                        buyerAuthenticationDomain = "issuer-a",
                        buyerUid = 1L,
                        recipientInfo = historicalPO,
                        currency = "CNY",
                        itemsSubtotal = BigDecimal.valueOf(100),
                        payableAmount = BigDecimal.valueOf(100),
                        createTime = now,
                        updateTime = now,
                        items = mutableListOf(testItemPO()),
                    )

                val order = converter.toDomain(orderPO)
                order.recipientInfo.name shouldBe ""
                // 历史数据无邮编与清关字段，重建后应为默认值
                order.recipientInfo.postalCode shouldBe null
                order.recipientInfo.customsFields shouldBe emptyMap()
            }
        }

        test(
            "consigneePhone and consigneeEmail both null defaults to ContractInfo with null phoneNumber and null email"
        ) {
            checkAll(
                100,
                i18nGeoAddressArb,
                digitCodeArb,
                nonBlankStringArb,
                optionalDetailAddressArb,
            ) { address, districtCode, consigneeName, detailAddress ->
                // Simulate historical data: both phone and email are null
                val historicalPO =
                    RecipientInfoPO(
                        consigneeName = consigneeName,
                        consigneePhone = null,
                        consigneeEmail = null,
                        countryCode = address.countryCode.value,
                        districtCode = districtCode,
                        shippingAddress = address,
                        detailAddress = detailAddress,
                    )

                val orderPO =
                    OrderPO(
                        id = 1L,
                        merchantId = 1L,
                        buyerAuthenticationDomain = "issuer-a",
                        buyerUid = 1L,
                        recipientInfo = historicalPO,
                        currency = "CNY",
                        itemsSubtotal = BigDecimal.valueOf(100),
                        payableAmount = BigDecimal.valueOf(100),
                        createTime = now,
                        updateTime = now,
                        items = mutableListOf(testItemPO()),
                    )

                val order = converter.toDomain(orderPO)
                order.recipientInfo.contractInfo.phoneNumber shouldBe null
                order.recipientInfo.contractInfo.email shouldBe null
            }
        }
    })

private fun testItemPO() =
    OrderItemPO(
        id = 1,
        orderId = 1,
        skuId = 1,
        spuId = 1,
        offerId = 1,
        storeId = 1,
        offerVersion = 1,
        fulfillmentNodeId = "DEFAULT",
        channelId = "ONLINE",
        goodsName = "test",
        skuDescription = "test",
        quantity = 1,
        unitPrice = BigDecimal.valueOf(100),
    )
