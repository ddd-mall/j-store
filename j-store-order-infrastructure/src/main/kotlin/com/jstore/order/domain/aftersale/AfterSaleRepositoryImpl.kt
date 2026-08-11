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

import com.jstore.common.errors.BusinessError
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.utils.*
import com.jstore.order.domain.aftersale.persistence.*
import com.jstore.order.domain.order.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class AfterSaleRepositoryImpl(
    private val roots: AfterSalePOJpaRepository,
    private val capacities: AfterSaleCapacityPOJpaRepository,
    private val receipts: AfterSaleCommandReceiptPOJpaRepository,
    private val sequence: SnowFlakSequence,
) : AfterSaleRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: AfterSale): AfterSale {
        return roots.save(toPO(entity)).let(::toDomain)
    }

    override fun findById(id: AfterSaleId) = roots.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByOrderId(orderId: OrderId) =
        roots.findByOrderIdOrderByCreateTimeDesc(orderId.value).map(::toDomain)

    override fun findReceipt(actorId: Long, type: AfterSaleCommandType, key: String) =
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
    override fun createWithAllocation(
        afterSale: AfterSale,
        ceilings: List<RefundCapacityCeiling>,
        receipt: AfterSaleCommandReceipt,
    ): Result<AfterSale, BusinessError> {
        val itemIds = afterSale.items.map { it.orderItemId.value }.sorted()
        if (ceilings.map { it.orderItemId.value }.sorted() != itemIds) {
            return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
        }
        val expectedByItem = ceilings.associateBy { it.orderItemId.value }
        capacities.findAllById(itemIds).forEach { actual ->
            val expected =
                expectedByItem[actual.orderItemId]
                    ?: return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
            verifyCeiling(expected, actual)?.let {
                return Failure(it)
            }
        }
        ceilings
            .sortedBy { it.orderItemId.value }
            .forEach { ceiling ->
                capacities.initialize(
                    ceiling.orderItemId.value,
                    ceiling.orderId.value,
                    ceiling.quantity,
                    ceiling.amount.toBigDecimal(),
                )
            }
        val locked = capacities.lockAll(itemIds).associateBy { it.orderItemId }
        ceilings.forEach { expected ->
            verifyCeiling(expected, locked[expected.orderItemId.value])?.let {
                return Failure(it)
            }
        }
        afterSale.items.forEach { item ->
            val capacity =
                locked[item.orderItemId.value]
                    ?: return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
            val quantityAfter =
                capacity.requestedQuantity + capacity.approvedQuantity + item.requestedQuantity
            val amountAfter =
                capacity.requestedAmount +
                    capacity.approvedAmount +
                    item.requestedAmount.toBigDecimal()
            if (quantityAfter > capacity.quantityCeiling || amountAfter > capacity.amountCeiling) {
                return Failure(AfterSaleErrors.CAPACITY_EXCEEDED)
            }
        }
        claimReceipt(receipt)?.let {
            return it
        }
        afterSale.items
            .sortedBy { it.orderItemId.value }
            .forEach { item ->
                val capacity = requireNotNull(locked[item.orderItemId.value])
                capacity.requestedQuantity += item.requestedQuantity
                capacity.requestedAmount += item.requestedAmount.toBigDecimal()
                capacities.save(capacity)
            }
        roots.save(toPO(afterSale))
        return Success(afterSale)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun saveDecision(
        afterSale: AfterSale,
        allocationAction: AllocationAction,
        receipt: AfterSaleCommandReceipt,
    ): Result<AfterSale, BusinessError> {
        val persisted =
            roots.findByIdForUpdate(afterSale.id.value) ?: return Failure(AfterSaleErrors.NOT_FOUND)
        if (persisted.version != afterSale.version)
            return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
        val locked =
            capacities.lockAll(afterSale.items.map { it.orderItemId.value }.sorted()).associateBy {
                it.orderItemId
            }
        afterSale.items.forEach { item ->
            val capacity =
                locked[item.orderItemId.value]
                    ?: return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
            if (
                capacity.requestedQuantity < item.requestedQuantity ||
                    capacity.requestedAmount < item.requestedAmount.toBigDecimal()
            ) {
                return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
            }
        }
        claimReceipt(receipt)?.let {
            return it
        }
        afterSale.items
            .sortedBy { it.orderItemId.value }
            .forEach { item ->
                val capacity = requireNotNull(locked[item.orderItemId.value])
                capacity.requestedQuantity -= item.requestedQuantity
                capacity.requestedAmount -= item.requestedAmount.toBigDecimal()
                if (allocationAction == AllocationAction.APPROVE) {
                    capacity.approvedQuantity += item.requestedQuantity
                    capacity.approvedAmount += item.requestedAmount.toBigDecimal()
                }
                capacities.save(capacity)
            }
        roots.save(toPO(afterSale))
        return Success(afterSale)
    }

    private fun verifyCeiling(
        expected: RefundCapacityCeiling,
        actual: AfterSaleCapacityPO?,
    ): BusinessError? {
        actual ?: return AfterSaleErrors.CONCURRENT_MODIFICATION
        return if (
            actual.orderId != expected.orderId.value ||
                actual.quantityCeiling != expected.quantity ||
                actual.amountCeiling.compareTo(expected.amount.toBigDecimal()) != 0
        )
            AfterSaleErrors.CAPACITY_EXCEEDED
        else null
    }

    private fun claimReceipt(expected: AfterSaleCommandReceipt): Result<AfterSale, BusinessError>? {
        val inserted =
            receipts.tryInsert(
                sequence.nextId(),
                expected.actorId,
                expected.type.name,
                expected.key,
                expected.requestHash,
                expected.afterSaleId.value,
                expected.resultStatus.name,
                expected.createdAt,
            )
        if (inserted == 1) return null
        val actual =
            findReceipt(expected.actorId, expected.type, expected.key)
                ?: return Failure(AfterSaleErrors.CONCURRENT_MODIFICATION)
        if (actual.requestHash != expected.requestHash)
            return Failure(AfterSaleErrors.IDEMPOTENCY_CONFLICT)
        return findById(actual.afterSaleId)?.let(::Success) ?: Failure(AfterSaleErrors.NOT_FOUND)
    }

    internal fun toPO(a: AfterSale) =
        AfterSalePO(
            id = a.id.value,
            orderId = a.orderId.value,
            applicantId = a.applicantId.value,
            merchantId = a.merchantId.value,
            status = a.status.name,
            reasonCategory = a.reason.category.name,
            reasonDescription = a.reason.description,
            fulfillmentStatus = a.fulfillmentSnapshot.status.name,
            requireReturn = a.fulfillmentSnapshot.requireReturn,
            reviewerId = a.reviewDecision?.reviewerId?.value,
            reviewedAt = a.reviewDecision?.reviewedAt,
            rejectionReason = a.reviewDecision?.rejectionReason,
            cancelledAt = a.cancelledAt,
            returnReceivedAt = a.returnReceivedAt,
            refundId = a.refundId,
            refundFailureReason = a.refundFailureReason,
            createTime = a.createTime,
            updateTime = a.updateTime,
            version = a.version,
            items =
                a.items
                    .map {
                        AfterSaleItemPO(
                            it.id.value,
                            a.id.value,
                            it.orderId.value,
                            it.orderItemId.value,
                            it.requestedQuantity,
                            it.requestedAmount.toBigDecimal(),
                            it.currency,
                            it.eligibilitySnapshot.refundableQuantity,
                            it.eligibilitySnapshot.refundableAmount.toBigDecimal(),
                            it.eligibilitySnapshot.goods.skuId,
                            it.eligibilitySnapshot.goods.spuId,
                            it.eligibilitySnapshot.goods.goodsName,
                            it.eligibilitySnapshot.goods.skuDescription,
                        )
                    }
                    .toMutableList(),
        )

    internal fun toDomain(p: AfterSalePO): AfterSale =
        AfterSaleImpl(
            id = AfterSaleId(p.id),
            orderId = OrderId(p.orderId),
            applicantId = ApplicantActorId(p.applicantId),
            merchantId = MerchantActorId(p.merchantId),
            _status = AfterSaleStatus.valueOf(p.status),
            reason = RefundReason(RefundCategory.valueOf(p.reasonCategory), p.reasonDescription),
            fulfillmentSnapshot =
                FulfillmentSnapshot(
                    FulfillmentStatus.valueOf(p.fulfillmentStatus),
                    p.requireReturn,
                ),
            items =
                p.items.map {
                    val g = GoodsSnapshot(it.skuId, it.spuId, it.goodsName, it.skuDescription)
                    AfterSaleItemImpl(
                        AfterSaleItemId(it.id),
                        OrderId(it.orderId),
                        OrderItemId(it.orderItemId),
                        it.requestedQuantity,
                        Price.fromBigDecimal(it.requestedAmount),
                        it.currency,
                        RefundEligibilitySnapshot(
                            OrderItemId(it.orderItemId),
                            it.eligibleQuantity,
                            Price.fromBigDecimal(it.eligibleAmount),
                            it.currency,
                            g,
                        ),
                    )
                },
            _reviewDecision =
                p.reviewerId?.let {
                    ReviewDecision(MerchantActorId(it), p.reviewedAt!!, p.rejectionReason)
                },
            _cancelledAt = p.cancelledAt,
            _returnReceivedAt = p.returnReceivedAt,
            _refundId = p.refundId,
            _refundFailureReason = p.refundFailureReason,
            createTime = p.createTime,
            _updateTime = p.updateTime,
            version = p.version,
        )
}
