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
package com.jstore.warehouse.domain

import com.jstore.warehouse.domain.persistence.PhysicalStockPO
import com.jstore.warehouse.domain.persistence.PhysicalStockPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class PhysicalStockRepositoryImpl(private val jpa: PhysicalStockPOJpaRepository) :
    PhysicalStockRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: PhysicalStock): PhysicalStock = toDomain(jpa.save(toPO(entity)))

    override fun findById(id: PhysicalStockId): PhysicalStock? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    private fun toPO(stock: PhysicalStock) =
        PhysicalStockPO(
            stock.id.value,
            stock.skuId,
            stock.fulfillmentNodeId,
            stock.onHand,
            stock.sourceVersion,
            stock.persistenceVersion,
        )

    private fun toDomain(po: PhysicalStockPO) =
        PhysicalStock(
            PhysicalStockId(po.id),
            po.skuId,
            po.fulfillmentNodeId,
            po.onHand,
            po.sourceVersion,
            po.persistenceVersion,
        )
}
