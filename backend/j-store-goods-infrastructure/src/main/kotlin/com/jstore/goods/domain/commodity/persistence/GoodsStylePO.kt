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
import java.time.LocalDateTime

@Entity
@Table(name = "goods_style")
class GoodsStylePO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "spu_id", nullable = false) var spuId: Long = 0,
    @Column(name = "main_images", columnDefinition = "jsonb", nullable = false)
    var mainImages: String = "[]",
    @Column(name = "detail_html", columnDefinition = "text", nullable = false)
    var detailHtml: String = "",
    @Column(name = "sku_images", columnDefinition = "jsonb", nullable = false)
    var skuImages: String = "{}",
    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
)
