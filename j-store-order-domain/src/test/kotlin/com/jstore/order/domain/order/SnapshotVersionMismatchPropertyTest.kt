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

import com.jstore.common.errors.BusinessError
import com.jstore.common.geo.*
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import com.jstore.order.domain.order.command.OrderCreateCMD
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import java.util.Locale

/**
 * Feature: commodity-draft-copy-on-write, Property 6: 快照版本不匹配时 OrderFactory 拒绝创建订单
 *
 * For any OrderCreateCMD，其中至少一个 OrderItemCMD 的 snapshotVersion 与 GoodsService 返回的最新 snapshotVersion
 * 不一致，OrderFactory.create 应返回 Failure（SNAPSHOT_VERSION_MISMATCH）， 且错误信息中包含不匹配的商品 SPU ID。
 *
 * **Validates: Requirements 10.2, 10.3, 11.2**
 */
class SnapshotVersionMismatchPropertyTest :
    FunSpec({
        val sampleAddress =
            I18nGeoAddress(
                countryCode = CountryCode.CN,
                components =
                    listOf(
                        AddressComponent(
                            code = "110000",
                            level = DivisionLevel(1, "省"),
                            names = mapOf(Locale.SIMPLIFIED_CHINESE to "北京市"),
                            defaultLocale = Locale.SIMPLIFIED_CHINESE,
                        )
                    ),
            )

        val stubGeoAddressService =
            object : GeoAddressService {
                override fun getByCode(
                    countryCode: String,
                    addressCode: String,
                ): Result<I18nGeoAddress, BusinessError> {
                    return Success(sampleAddress)
                }
            }

        val snowFlakSequence = SnowFlakSequence(1, 1)

        // Generator for SPU IDs (positive longs)
        val spuIdArb: Arb<Long> = Arb.long(1L..10000L)

        // Generator for the goods service snapshot version (the "real" version)
        val goodsVersionArb: Arb<Long> = Arb.long(1L..1000L)

        // Generator for a version offset that is guaranteed non-zero (to create mismatch)
        val nonZeroOffsetArb: Arb<Long> =
            Arb.choice(
                Arb.long(1L..100L),
                Arb.long(-100L..-1L),
            )

        test(
            "OrderFactory.create() returns Failure(SNAPSHOT_VERSION_MISMATCH) when snapshotVersion mismatches"
        ) {
            checkAll(100, spuIdArb, goodsVersionArb, nonZeroOffsetArb) { spuId, goodsVersion, offset
                ->
                val cmdSnapshotVersion = goodsVersion + offset

                val goodsService =
                    object : GoodsService {
                        override fun queryGoods(goodsId: List<GoodsId>): List<GoodsInfo> {
                            return goodsId.map {
                                GoodsInfo(
                                    id = it,
                                    merchantId = 7,
                                    snapshotVersion = goodsVersion,
                                    spuName = "测试商品",
                                    skuName = "默认规格",
                                    attributes = emptyList(),
                                )
                            }
                        }
                    }

                val factory =
                    OrderFactoryImpl(
                        snowFlakSequence,
                        goodsService,
                        stubGeoAddressService,
                        testOfferService(),
                    )

                val cmd =
                    OrderCreateCMD(
                        buyerUid = 1L,
                        merchantId = 7,
                        buyerPhone = "+8613800138000",
                        buyerName = "买家",
                        recipientInfo =
                            OrderCreateCMD.RecipientInfoCMD(
                                consigneeName = "张三",
                                countryCode = "CN",
                                consigneeContractInfo =
                                    OrderCreateCMD.ContractInfoCMD(
                                        phoneNumber = PhoneNumber("+8613900139000"),
                                        emailAddress = null,
                                    ),
                                shippingDistrictCode = "110000",
                                shippingDetailAddress = "朝阳区三里屯",
                            ),
                        items =
                            listOf(
                                OrderCreateCMD.OrderItemCMD(
                                    spuId = spuId,
                                    skuId = 1L,
                                    quantity = 1,
                                    snapshotVersion = cmdSnapshotVersion,
                                )
                            ),
                    )

                val result = factory.create(cmd)

                result.shouldBeInstanceOf<Failure<BusinessError>>()
                result.error.errorCode shouldBe "Order.Snapshot.VersionMismatch"
                result.error.message shouldContain spuId.toString()
            }
        }
    })
