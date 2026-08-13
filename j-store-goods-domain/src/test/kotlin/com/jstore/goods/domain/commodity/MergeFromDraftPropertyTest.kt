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

import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: commodity-draft-copy-on-write, Property 4: mergeFromDraft 正确合并数据并递增版本

/**
 * Property 4: mergeFromDraft 正确合并数据并递增版本
 *
 * For any PUBLISHED 状态的源 SPU 和任意包含至少一个 SKU 的草稿 SPU， 调用 source.mergeFromDraft(draft) 后，源 SPU 应满足：
 * name 等于草稿的 name，description 等于草稿的 description， SKU 列表与草稿的 SKU 列表内容一致，version 等于合并前的 version + 1，
 * status 保持 PUBLISHED 不变。
 *
 * **Validates: Requirements 6.2, 6.3, 6.4, 9.2, 9.3, 9.4**
 */
class MergeFromDraftPropertyTest :
    FunSpec({

        // Generator for a single SKU with unique attribute value
        fun skuArb(attrValue: String): Arb<Sku> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE),
                Arb.string(1..20),
                Arb.long(1L..999999L),
            ) { skuIdVal, skuName, priceFen ->
                SkuImpl(
                    id = SkuId(skuIdVal),
                    skuName = skuName,
                    attributes = listOf(Attribute("variant", attrValue)),
                )
            }

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
                    )
                },
                1..5,
            )

        // Generator for an PUBLISHED source SPU
        val onSaleSpuArb: Arb<SpuImpl> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE),
                Arb.string(1..50),
                Arb.string(0..100),
                Arb.long(1L..10000L),
                skuListArb,
            ) { spuIdVal, name, description, version, skus ->
                SpuImpl(
                    id = SpuId(spuIdVal),
                    name = name,
                    description = description,
                    _status = CommodityStatus.PUBLISHED,
                    _skus = skus.toMutableList(),
                    _version = version,
                )
            }

        // Generator for a draft SPU with at least one SKU
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

        test("mergeFromDraft should merge draft data into PUBLISHED source and increment version") {
            checkAll(100, onSaleSpuArb, draftSpuArb) { source, draft ->
                val versionBefore = source.version

                val result = source.mergeFromDraft(draft)

                result.shouldBeInstanceOf<Success<Unit>>()
                source.name shouldBe draft.name
                source.description shouldBe draft.description
                source.skus.map { it.skuName } shouldBe draft.skus.map { it.skuName }
                source.skus.map { it.attributes } shouldBe draft.skus.map { it.attributes }
                source.skus.map { it.id } shouldBe draft.skus.map { it.sourceSkuId ?: it.id }
                source.version shouldBe versionBefore + 1
                source.status shouldBe CommodityStatus.PUBLISHED
            }
        }
    })
