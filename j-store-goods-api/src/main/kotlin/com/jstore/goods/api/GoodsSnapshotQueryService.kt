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
package com.jstore.goods.api

interface GoodsSnapshotQueryService {
    fun queryLatestSnapshots(spuIds: List<Long>): List<GoodsSnapshotInfo>
}

data class GoodsSnapshotInfo(
    val spuId: Long,
    val merchantId: Long,
    val snapshotVersion: Long,
    val spuName: String,
    val skuSnapshots: List<GoodsSkuSnapshotInfo>,
    val description: String = "",
    val mainImages: List<String> = emptyList(),
    val detailHtml: String = "",
    val productTypeId: Long? = null,
    val productAttributes: List<Pair<String, String>> = emptyList(),
    val brandId: Long? = null,
    val categoryIds: Set<Long> = emptySet(),
    val localizedNames: Map<String, String> = emptyMap(),
    val localizedDescriptions: Map<String, String> = emptyMap(),
)

data class GoodsSkuSnapshotInfo(
    val skuId: Long,
    val skuName: String,
    val attributes: List<Pair<String, String>>,
    val imageKeys: List<String> = emptyList(),
)
