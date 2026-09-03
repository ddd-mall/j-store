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

import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.persistence.*
import com.jstore.order.domain.order.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class AfterSaleRepositoryImpl(private val roots: AfterSalePOJpaRepository) : AfterSaleRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: AfterSale): AfterSale {
        return roots.save(toPO(aggregate)).let(::toDomain)
    }

    override fun findById(id: AfterSaleId) = roots.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByOrderId(orderId: OrderId) =
        roots.findByOrderIdOrderByCreateTimeDesc(orderId.value).map(::toDomain)

    @Transactional(propagation = Propagation.MANDATORY)
    override fun findByIdForUpdate(id: AfterSaleId) =
        roots.findByIdForUpdate(id.value)?.let(::toDomain)

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
