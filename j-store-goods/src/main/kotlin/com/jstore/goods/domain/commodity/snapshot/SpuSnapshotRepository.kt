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
package com.jstore.goods.domain.commodity.snapshot

import com.jstore.common.framework.Repository
import com.jstore.goods.domain.commodity.SpuId

interface SpuSnapshotRepository : Repository<SpuSnapshotId, SpuSnapshot> {

    /** 根据 SPU ID 和版本号查询快照 */
    fun findBySpuIdAndVersion(spuId: SpuId, version: Long): SpuSnapshot?

    /** 查询某个 SPU 的最新快照 */
    fun findLatestBySpuId(spuId: SpuId): SpuSnapshot?
}
