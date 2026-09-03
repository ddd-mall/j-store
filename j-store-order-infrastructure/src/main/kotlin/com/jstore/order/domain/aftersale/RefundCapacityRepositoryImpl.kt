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
package com.jstore.order.domain.aftersale

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.persistence.AfterSaleCapacityPO
import com.jstore.order.domain.aftersale.persistence.AfterSaleCapacityPOJpaRepository
import com.jstore.order.domain.aftersale.persistence.AfterSaleCommandReceiptPOJpaRepository
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class RefundCapacityRepositoryImpl(private val capacities: AfterSaleCapacityPOJpaRepository) :
    RefundCapacityRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun initializeIfAbsent(capacities: List<RefundCapacity>) {
        capacities
            .sortedBy { it.id.value }
            .forEach {
                this.capacities.initialize(
                    it.id.value,
                    it.orderId.value,
                    it.quantityCeiling,
                    it.amountCeiling.toBigDecimal(),
                )
            }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockAll(ids: Collection<OrderItemId>): List<RefundCapacity> =
        capacities.lockAll(ids.map { it.value }.sorted()).map(::toDomain)

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: RefundCapacity): RefundCapacity =
        capacities.save(toPO(aggregate)).let(::toDomain)

    override fun findById(id: OrderItemId): RefundCapacity? =
        capacities.findById(id.value).orElse(null)?.let(::toDomain)

    private fun toDomain(po: AfterSaleCapacityPO) =
        RefundCapacity(
            id = OrderItemId(po.orderItemId),
            orderId = OrderId(po.orderId),
            quantityCeiling = po.quantityCeiling,
            amountCeiling = Price.fromBigDecimal(po.amountCeiling),
            requestedQuantity = po.requestedQuantity,
            requestedAmount = Price.fromBigDecimal(po.requestedAmount),
            approvedQuantity = po.approvedQuantity,
            approvedAmount = Price.fromBigDecimal(po.approvedAmount),
            persistenceVersion = po.version,
        )

    private fun toPO(capacity: RefundCapacity) =
        AfterSaleCapacityPO(
            orderItemId = capacity.id.value,
            orderId = capacity.orderId.value,
            quantityCeiling = capacity.quantityCeiling,
            amountCeiling = capacity.amountCeiling.toBigDecimal(),
            requestedQuantity = capacity.requestedQuantity,
            requestedAmount = capacity.requestedAmount.toBigDecimal(),
            approvedQuantity = capacity.approvedQuantity,
            approvedAmount = capacity.approvedAmount.toBigDecimal(),
            version = capacity.persistenceVersion,
        )
}

@Repository
class AfterSaleCommandReceiptStoreImpl(
    private val receipts: AfterSaleCommandReceiptPOJpaRepository,
    private val sequence: SnowFlakSequence,
) : AfterSaleCommandReceiptStore {
    override fun find(actorId: Long, type: AfterSaleCommandType, key: String) =
        receipts.findByActorIdAndCommandTypeAndIdempotencyKey(actorId, type.name, key)?.let {
            AfterSaleCommandReceipt(
                it.actorId,
                type,
                it.idempotencyKey,
                it.requestHash,
                AfterSaleId(it.afterSaleId),
                AfterSaleStatus.valueOf(it.resultStatus),
                it.createdAt,
            )
        }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun claim(receipt: AfterSaleCommandReceipt): Boolean =
        receipts.tryInsert(
            sequence.nextId(),
            receipt.actorId,
            receipt.type.name,
            receipt.key,
            receipt.requestHash,
            receipt.afterSaleId.value,
            receipt.resultStatus.name,
            receipt.createdAt,
        ) == 1
}
