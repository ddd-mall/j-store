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

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price

class SkuId(override val value: Long) : Id<Long>(value)

interface Sku : Entity<SkuId> {
    /** SKU 名称（如 "红色 / XL"） */
    val skuName: String

    /** 销售属性列表 */
    val attributes: List<Attribute<String, String>>

    /** SKU 单价 */
    val price: Price

    /** 商家内部货号 */
    val merchantCode: String?

    /** 标准条形码（EAN/UPC） */
    val barcode: String?
}

class SkuImpl(
    override val id: SkuId,
    override val skuName: String,
    override val attributes: List<Attribute<String, String>>,
    override val price: Price,
    override val merchantCode: String? = null,
    override val barcode: String? = null,
) : Sku
