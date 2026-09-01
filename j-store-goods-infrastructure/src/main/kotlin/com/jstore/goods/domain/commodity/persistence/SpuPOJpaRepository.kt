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
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpuPOJpaRepository : JpaRepository<SpuPO, Long> {

    @Query(
        "select distinct s from SpuPO s join fetch s.skus sku where sku.id in :skuIds and s.status = :status"
    )
    fun findBySkuIdsAndStatus(
        @Param("skuIds") skuIds: List<Long>,
        @Param("status") status: CommodityStatus,
    ): List<SpuPO>

    /** 根据 source_spu_id 和 status 查询草稿副本 */
    fun findBySourceSpuIdAndStatus(sourceSpuId: Long, status: CommodityStatus): SpuPO?
}
