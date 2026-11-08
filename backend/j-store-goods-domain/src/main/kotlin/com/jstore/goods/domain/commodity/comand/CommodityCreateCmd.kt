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
package com.jstore.goods.domain.commodity.comand

import com.jstore.goods.domain.brand.BrandId
import com.jstore.goods.domain.category.CategoryId
import com.jstore.goods.domain.commodity.Attribute
import com.jstore.goods.domain.commodity.SpuId
import com.jstore.goods.domain.content.LocalizedText
import com.jstore.goods.domain.producttype.ProductTypeId

data class CommodityCreateCmd(
    val spuId: SpuId?,
    val merchantId: Long,
    val spuName: String,
    val description: String = "",
    val productTypeId: ProductTypeId? = null,
    val productAttributes: List<Attribute<String, String>> = emptyList(),
    val brandId: BrandId? = null,
    val categoryIds: Set<CategoryId> = emptySet(),
    val localizedNames: LocalizedText? = null,
    val localizedDescriptions: LocalizedText? = null,
)
