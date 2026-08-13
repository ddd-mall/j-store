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

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.goods.domain.commodity.Attribute
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.commodity.Sku
import com.jstore.goods.domain.content.LocalizedText

data class ProductTypeId(override val value: Long) : Id<Long>(value) {
    init {
        require(value != 0L) { "product type id must not be zero" }
    }
}

enum class AttributeLevel {
    PRODUCT,
    VARIANT,
}

enum class AttributeValueType {
    TEXT,
    NUMBER,
    BOOLEAN,
    ENUM,
}

data class AttributeDefinition(
    val code: String,
    val label: LocalizedText,
    val level: AttributeLevel,
    val valueType: AttributeValueType,
    val required: Boolean = false,
    val variantAxis: Boolean = false,
    val allowedValues: Set<String> = emptySet(),
) {
    init {
        require(code.matches(Regex("[a-z][a-z0-9_]{1,63}"))) { "invalid attribute code: $code" }
        require(!variantAxis || level == AttributeLevel.VARIANT) {
            "variant axis must be variant-level"
        }
        require(valueType == AttributeValueType.ENUM || allowedValues.isEmpty()) {
            "allowed values are only valid for enum attributes"
        }
        require(valueType != AttributeValueType.ENUM || allowedValues.isNotEmpty()) {
            "enum attribute must define allowed values"
        }
    }

    internal fun accepts(value: String): Boolean =
        when (valueType) {
            AttributeValueType.TEXT -> value.isNotBlank()
            AttributeValueType.NUMBER -> value.toBigDecimalOrNull() != null
            AttributeValueType.BOOLEAN -> value == "true" || value == "false"
            AttributeValueType.ENUM -> value in allowedValues
        }
}

interface ProductType : AggregateRoot<ProductTypeId> {
    val merchantId: MerchantId
    val name: LocalizedText
    val definitions: List<AttributeDefinition>

    fun validate(
        productAttributes: List<Attribute<String, String>>,
        skus: List<Sku>,
    ): Result<Unit, BusinessError>
}

class ProductTypeImpl(
    override val id: ProductTypeId,
    override val merchantId: MerchantId,
    override val name: LocalizedText,
    override val definitions: List<AttributeDefinition>,
) : ProductType {
    init {
        require(definitions.map { it.code }.distinct().size == definitions.size) {
            "attribute definition codes must be unique"
        }
    }

    override fun validate(
        productAttributes: List<Attribute<String, String>>,
        skus: List<Sku>,
    ): Result<Unit, BusinessError> {
        validateLevel(productAttributes, AttributeLevel.PRODUCT).onFailure {
            return Failure(it)
        }
        skus.forEach { sku ->
            validateLevel(sku.attributes, AttributeLevel.VARIANT).onFailure {
                return Failure(it)
            }
        }

        val axes = definitions.filter { it.variantAxis }.map { it.code }.sorted()
        if (axes.isNotEmpty()) {
            val combinations = skus.map { sku ->
                val values = sku.attributes.associate { it.key to it.value }
                axes.map { code -> values[code] }
            }
            if (combinations.distinct().size != combinations.size) {
                return Failure(ProductTypeErrors.DUPLICATE_VARIANT_COMBINATION)
            }
        }
        return Success(Unit)
    }

    private fun validateLevel(
        values: List<Attribute<String, String>>,
        level: AttributeLevel,
    ): Result<Unit, BusinessError> {
        if (values.map { it.key }.distinct().size != values.size) {
            return Failure(ProductTypeErrors.DUPLICATE_ATTRIBUTE)
        }
        val definitionsForLevel = definitions.filter { it.level == level }.associateBy { it.code }
        if (values.any { it.key !in definitionsForLevel }) {
            return Failure(ProductTypeErrors.UNKNOWN_ATTRIBUTE)
        }
        if (
            definitionsForLevel.values.any { definition ->
                definition.required && values.none { it.key == definition.code }
            }
        ) {
            return Failure(ProductTypeErrors.REQUIRED_ATTRIBUTE_MISSING)
        }
        if (
            values.any { value ->
                definitionsForLevel.getValue(value.key).accepts(value.value).not()
            }
        ) {
            return Failure(ProductTypeErrors.INVALID_ATTRIBUTE_VALUE)
        }
        return Success(Unit)
    }
}

object ProductTypeErrors {
    val NOT_FOUND = BusinessError("商品类型不存在", "Catalog.ProductType.NotFound", 404)
    val MERCHANT_MISMATCH =
        BusinessError("商品类型不属于当前商户", "Catalog.ProductType.MerchantMismatch", 400)
    val UNKNOWN_ATTRIBUTE =
        BusinessError("商品属性未在商品类型中定义", "Catalog.ProductType.UnknownAttribute", 400)
    val DUPLICATE_ATTRIBUTE = BusinessError("商品属性重复", "Catalog.ProductType.DuplicateAttribute", 400)
    val REQUIRED_ATTRIBUTE_MISSING =
        BusinessError("缺少商品类型要求的必填属性", "Catalog.ProductType.RequiredAttributeMissing", 400)
    val INVALID_ATTRIBUTE_VALUE =
        BusinessError("商品属性值不符合类型定义", "Catalog.ProductType.InvalidAttributeValue", 400)
    val DUPLICATE_VARIANT_COMBINATION =
        BusinessError("SKU变体属性组合重复", "Catalog.ProductType.DuplicateVariantCombination", 400)
}
