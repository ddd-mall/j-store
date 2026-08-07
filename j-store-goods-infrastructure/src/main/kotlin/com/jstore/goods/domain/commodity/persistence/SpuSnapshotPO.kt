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
@Table(
    name = "spu_snapshot",
    uniqueConstraints = [UniqueConstraint(columnNames = ["spu_id", "snapshot_version"])],
)
class SpuSnapshotPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "spu_id", nullable = false) var spuId: Long = 0,
    @Column(name = "snapshot_version", nullable = false) var snapshotVersion: Long = 0,
    @Column(name = "spu_name", nullable = false, length = 256) var spuName: String = "",
    @Column(name = "description", length = 2000) var description: String = "",

    /** 完整 SKU 快照 JSON 数组 */
    @Column(name = "sku_snapshots", columnDefinition = "jsonb", nullable = false)
    var skuSnapshots: String = "[]",
    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),
)
