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
package com.jstore.goods.domain.commodity.persistence

import jakarta.persistence.*

@Entity
@Table(name = "sku")
class SkuPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "spu_id", nullable = false, insertable = false, updatable = false)
    var spuId: Long = 0,
    @Column(name = "sku_name", nullable = false, length = 256) var skuName: String = "",

    /** 销售属性 JSON，如 [{"key":"颜色","value":"红色"},{"key":"尺码","value":"XL"}] */
    @Column(name = "attributes", columnDefinition = "jsonb") var attributes: String = "[]",
    @Column(name = "merchant_code", length = 128) var merchantCode: String? = null,
    @Column(name = "barcode", length = 64) var barcode: String? = null,
)
