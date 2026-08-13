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
package com.jstore.goods.domain.producttype

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.Attribute
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.commodity.SkuId
import com.jstore.goods.domain.commodity.SkuImpl
import com.jstore.goods.domain.content.LocalizedText
import kotlin.test.Test
import kotlin.test.assertIs

class ProductTypeValidationTest {
    private val productType =
        ProductTypeImpl(
            id = ProductTypeId(1),
            merchantId = MerchantId(7),
            name = LocalizedText.of("zh-CN" to "服装"),
            definitions =
                listOf(
                    AttributeDefinition(
                        code = "color",
                        label = LocalizedText.of("zh-CN" to "颜色"),
                        level = AttributeLevel.VARIANT,
                        valueType = AttributeValueType.ENUM,
                        required = true,
                        variantAxis = true,
                        allowedValues = setOf("red", "blue"),
                    ),
                    AttributeDefinition(
                        code = "weight",
                        label = LocalizedText.of("zh-CN" to "重量"),
                        level = AttributeLevel.PRODUCT,
                        valueType = AttributeValueType.NUMBER,
                    ),
                ),
        )

    @Test
    fun `valid typed attributes and unique variant combinations pass`() {
        val result =
            productType.validate(
                productAttributes = listOf(Attribute("weight", "1.25")),
                skus =
                    listOf(
                        SkuImpl(SkuId(1), "红色", listOf(Attribute("color", "red"))),
                        SkuImpl(SkuId(2), "蓝色", listOf(Attribute("color", "blue"))),
                    ),
            )

        assertIs<Success<Unit>>(result)
    }

    @Test
    fun `unknown wrong typed and illegal enum attributes are rejected`() {
        assertIs<Failure<*>>(
            productType.validate(
                listOf(Attribute("unknown", "x")),
                listOf(SkuImpl(SkuId(1), "红色", listOf(Attribute("color", "red")))),
            )
        )
        assertIs<Failure<*>>(
            productType.validate(
                listOf(Attribute("weight", "heavy")),
                listOf(SkuImpl(SkuId(1), "红色", listOf(Attribute("color", "red")))),
            )
        )
        assertIs<Failure<*>>(
            productType.validate(
                emptyList(),
                listOf(SkuImpl(SkuId(1), "绿色", listOf(Attribute("color", "green")))),
            )
        )
    }

    @Test
    fun `missing required attributes and duplicate variant axes are rejected`() {
        assertIs<Failure<*>>(
            productType.validate(emptyList(), listOf(SkuImpl(SkuId(1), "无颜色", emptyList())))
        )
        assertIs<Failure<*>>(
            productType.validate(
                emptyList(),
                listOf(
                    SkuImpl(SkuId(1), "红色一", listOf(Attribute("color", "red"))),
                    SkuImpl(SkuId(2), "红色二", listOf(Attribute("color", "red"))),
                ),
            )
        )
    }
}
