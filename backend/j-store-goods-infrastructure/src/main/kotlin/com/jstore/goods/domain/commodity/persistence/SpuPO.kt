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

import com.jstore.goods.domain.commodity.CommodityStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "spu")
class SpuPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "name", nullable = false, length = 256) var name: String = "",
    @Column(name = "description", length = 2000) var description: String = "",
    @Column(name = "product_type_id") var productTypeId: Long? = null,
    @Column(name = "product_attributes", columnDefinition = "jsonb", nullable = false)
    var productAttributes: String = "[]",
    @Column(name = "brand_id") var brandId: Long? = null,
    @Column(name = "category_ids", columnDefinition = "jsonb", nullable = false)
    var categoryIds: String = "[]",
    @Column(name = "localized_names", columnDefinition = "jsonb")
    var localizedNames: String? = null,
    @Column(name = "localized_descriptions", columnDefinition = "jsonb")
    var localizedDescriptions: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    var status: CommodityStatus = CommodityStatus.DRAFT,
    @Column(name = "version", nullable = false) var version: Long = 1,
    @Column(name = "source_spu_id") var sourceSpuId: Long? = null,
    @Version
    @Column(name = "persistence_version", nullable = false)
    var persistenceVersion: Long = 0,
    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
    @OneToMany(cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "spu_id")
    var skus: MutableList<SkuPO> = mutableListOf(),
)
