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

import com.jstore.common.utils.json.JsonUtils
import com.jstore.goods.domain.commodity.MerchantId
import com.jstore.goods.domain.content.LocalizedText
import com.jstore.goods.domain.producttype.persistence.ProductTypePO
import com.jstore.goods.domain.producttype.persistence.ProductTypePOJpaRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class ProductTypeRepositoryImpl(private val jpaRepository: ProductTypePOJpaRepository) :
    ProductTypeRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: ProductType): ProductType {
        val po =
            jpaRepository.findById(entity.id.value).orElse(null)?.also { existing ->
                existing.merchantId = entity.merchantId.value
                existing.name = JsonUtils.toJsonString(entity.name.values)
                existing.definitions = encodeDefinitions(entity.definitions)
                existing.updatedAt = LocalDateTime.now()
            }
                ?: ProductTypePO(
                    id = entity.id.value,
                    merchantId = entity.merchantId.value,
                    name = JsonUtils.toJsonString(entity.name.values),
                    definitions = encodeDefinitions(entity.definitions),
                )
        return toDomain(jpaRepository.save(po))
    }

    override fun findById(id: ProductTypeId): ProductType? =
        jpaRepository.findById(id.value).orElse(null)?.let(::toDomain)

    private fun encodeDefinitions(definitions: List<AttributeDefinition>): String =
        JsonUtils.toJsonString(
            definitions.map { definition ->
                mapOf(
                    "code" to definition.code,
                    "label" to definition.label.values,
                    "level" to definition.level.name,
                    "valueType" to definition.valueType.name,
                    "required" to definition.required,
                    "variantAxis" to definition.variantAxis,
                    "allowedValues" to definition.allowedValues.sorted(),
                )
            }
        )

    @Suppress("UNCHECKED_CAST")
    private fun toDomain(po: ProductTypePO): ProductType {
        val definitions: List<Map<String, Any?>> = JsonUtils.deserialize(po.definitions)
        return ProductTypeImpl(
            id = ProductTypeId(po.id),
            merchantId = MerchantId(po.merchantId),
            name = LocalizedText(JsonUtils.deserialize(po.name)),
            definitions =
                definitions.map { definition ->
                    AttributeDefinition(
                        code = definition.getValue("code") as String,
                        label =
                            LocalizedText((definition.getValue("label") as Map<String, String>)),
                        level = AttributeLevel.valueOf(definition.getValue("level") as String),
                        valueType =
                            AttributeValueType.valueOf(definition.getValue("valueType") as String),
                        required = definition["required"] as Boolean,
                        variantAxis = definition["variantAxis"] as Boolean,
                        allowedValues = (definition["allowedValues"] as List<String>).toSet(),
                    )
                },
        )
    }
}
