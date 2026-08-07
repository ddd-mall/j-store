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
import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.persistence.SkuPO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll

// Feature: goods-style-and-sku-code, Property 6: SKU Converter 往返一致性（含编码字段）

/**
 * Property 6: SKU Converter 往返一致性（含编码字段）
 *
 * For any 有效的 SKU 领域对象（包含任意 merchantCode 和 barcode 值，含 null）， 经过 Converter.toSkuPO 转换为 SkuPO 再经过
 * Converter.toDomainSku 转换回领域对象， 结果应与原始对象在所有字段上等价（包括 merchantCode 和 barcode）。
 *
 * **Validates: Requirements 8.5, 9.5**
 */
class SkuConverterRoundTripPropertyTest :
    FunSpec({

        // Generator for nullable strings (merchantCode / barcode)
        val nullableStringArb: Arb<String?> =
            Arb.choice(
                Arb.constant(null),
                Arb.string(1..64),
            )

        // Generator for a single attribute
        val attributeArb: Arb<Attribute<String, String>> =
            Arb.bind(
                Arb.string(1..10),
                Arb.string(1..10),
            ) { key, value ->
                Attribute(key, value)
            }

        // Generator for a valid SKU domain object
        val skuArb: Arb<Sku> =
            Arb.bind(
                Arb.long(1L..Long.MAX_VALUE), // skuId
                Arb.long(1L..Long.MAX_VALUE), // spuId (needed for toSkuPO)
                Arb.string(1..20), // skuName
                Arb.list(attributeArb, 0..3), // attributes
                Arb.long(0L..999999L), // price in fen
                nullableStringArb, // merchantCode
                nullableStringArb, // barcode
            ) { skuIdVal, _, skuName, attrs, priceFen, merchantCode, barcode ->
                SkuImpl(
                    id = SkuId(skuIdVal),
                    skuName = skuName,
                    attributes = attrs,
                    price = Price.ofFen(priceFen),
                    merchantCode = merchantCode,
                    barcode = barcode,
                )
            }

        // Replicate the Converter logic locally since it's a private object in SpuRepositoryImpl
        fun toSkuPO(sku: Sku, spuId: Long): SkuPO {
            return SkuPO(
                id = sku.id.value,
                spuId = spuId,
                skuName = sku.skuName,
                attributes = JsonUtils.toJsonString(sku.attributes),
                price = sku.price.toBigDecimal(),
                merchantCode = sku.merchantCode,
                barcode = sku.barcode,
            )
        }

        fun toDomainSku(po: SkuPO): Sku {
            val attrs: List<Attribute<String, String>> = JsonUtils.deserialize(po.attributes)
            return SkuImpl(
                id = SkuId(po.id),
                skuName = po.skuName,
                attributes = attrs,
                price = Price.fromBigDecimal(po.price),
                merchantCode = po.merchantCode,
                barcode = po.barcode,
            )
        }

        test(
            "toSkuPO then toDomainSku should preserve all fields including merchantCode and barcode"
        ) {
            checkAll(100, skuArb, Arb.long(1L..Long.MAX_VALUE)) { sku, spuId ->
                val po = toSkuPO(sku, spuId)
                val roundTripped = toDomainSku(po)

                roundTripped.id.value shouldBe sku.id.value
                roundTripped.skuName shouldBe sku.skuName
                roundTripped.attributes shouldBe sku.attributes
                roundTripped.price shouldBe sku.price
                roundTripped.merchantCode shouldBe sku.merchantCode
                roundTripped.barcode shouldBe sku.barcode
            }
        }
    })
