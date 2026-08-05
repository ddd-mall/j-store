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
package com.jstore.goods.domain.commodity

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: commodity-draft-copy-on-write, Property 5: mergeFromDraft 拒绝非 ON_SALE 目标

/**
 * Property 5: mergeFromDraft 拒绝非 ON_SALE 目标
 *
 * For any 状态不是 ON_SALE 的 SPU（即 DRAFT 或 OFF_SALE）， 调用 mergeFromDraft 应返回 Failure，且 SPU 的所有字段保持不变。
 *
 * **Validates: Requirements 9.5**
 */
class MergeFromDraftStatusGuardPropertyTest :
    FunSpec({

        // Non-ON_SALE statuses
        val nonOnSaleStatusArb: Arb<CommodityStatus> =
            Arb.of(
                CommodityStatus.DRAFT,
                CommodityStatus.OFF_SALE,
            )

        // Generator for a non-empty list of SKUs (1..5)
        val skuListArb: Arb<List<Sku>> =
            Arb.list(
                Arb.bind(
                    Arb.long(1L..Long.MAX_VALUE),
                    Arb.string(1..20),
                    Arb.long(1L..999999L),
                    Arb.string(1..10),
                ) { skuIdVal, skuName, priceFen, attrValue ->
                    SkuImpl(
                        id = SkuId(skuIdVal),
                        skuName = skuName,
                        attributes = listOf(Attribute("variant", attrValue)),
                        price = Price.ofFen(priceFen),
                    )
                },
                1..5,
            )

        // Generator for a non-ON_SALE SPU (target)
        val nonOnSaleSpuArb: Arb<SpuImpl> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE),
                Arb.string(1..50),
                Arb.string(0..100),
                Arb.long(1L..10000L),
                skuListArb,
                nonOnSaleStatusArb,
            ) { spuIdVal, name, description, version, skus, status ->
                SpuImpl(
                    id = SpuId(spuIdVal),
                    name = name,
                    description = description,
                    _status = status,
                    _skus = skus.toMutableList(),
                    _version = version,
                )
            }

        // Generator for a draft SPU (the source of merge data) with at least one SKU
        val draftSpuArb: Arb<SpuImpl> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE),
                Arb.string(1..50),
                Arb.string(0..100),
                skuListArb,
            ) { spuIdVal, name, description, skus ->
                SpuImpl(
                    id = SpuId(spuIdVal),
                    name = name,
                    description = description,
                    _status = CommodityStatus.DRAFT,
                    _skus = skus.toMutableList(),
                    _version = 1L,
                )
            }

        test(
            "mergeFromDraft should return Failure for non-ON_SALE SPU and leave all fields unchanged"
        ) {
            checkAll(100, nonOnSaleSpuArb, draftSpuArb) { target, draft ->
                // Capture original field values before the call
                val originalName = target.name
                val originalDescription = target.description
                val originalSkus = target.skus.toList()
                val originalVersion = target.version
                val originalStatus = target.status

                val result = target.mergeFromDraft(draft)

                result.shouldBeInstanceOf<Failure<*>>()
                target.name shouldBe originalName
                target.description shouldBe originalDescription
                target.skus shouldBe originalSkus
                target.version shouldBe originalVersion
                target.status shouldBe originalStatus
            }
        }
    })
