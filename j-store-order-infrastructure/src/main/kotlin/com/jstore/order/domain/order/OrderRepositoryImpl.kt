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
package com.jstore.order.domain.order

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.query.Page
import com.jstore.common.query.SortedPage
import com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import com.jstore.order.domain.order.persistence.RecipientInfoPO
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class OrderRepositoryImpl(
    private val jpaRepository: OrderPOJpaRepository,
    private val entityManager: EntityManager,
) : OrderRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun add(order: Order) {
        val po = Converter.toPO(order)
        entityManager.persist(po)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: Order): Order {
        val po = Converter.toPO(entity)
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: OrderId): Order? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findByBuyerUserId(uid: Long): List<Order> {
        return jpaRepository.findByBuyerUid(uid).map { Converter.toDomain(it) }
    }

    override fun findBySourceOrderPlanId(orderPlanId: Long): Order? =
        jpaRepository.findBySourceOrderPlanId(orderPlanId)?.let(Converter::toDomain)

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
        val pageable =
            PageRequest.of(currentPage - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"))
        val page = jpaRepository.findByBuyerUid(uid, pageable)
        return SortedPage(
            currentPage = currentPage,
            totalElements = page.totalElements.toInt(),
            records = page.content.map { Converter.toDomain(it) },
        )
    }

    internal object Converter {

        fun toPO(order: Order): OrderPO {
            val si = order.recipientInfo
            val recipientInfoPO =
                RecipientInfoPO(
                    consigneeName = si.name,
                    consigneePhone = si.contractInfo.phoneNumber?.value,
                    consigneeEmail = si.contractInfo.email,
                    countryCode = si.shippingAddress.countryCode.value,
                    districtCode = si.shippingAddress.getLeafCode(),
                    shippingAddress = si.shippingAddress,
                    detailAddress = si.shippingDetailAddress,
                    postalCode = si.postalCode,
                    customsFields = si.customsFields.ifEmpty { null },
                )
            return OrderPO(
                id = order.id.value,
                sourceTradeId = order.sourceTradeId,
                sourceOrderPlanId = order.sourceOrderPlanId,
                sourcePlanDigest = order.sourcePlanDigest,
                merchantId = order.merchantId.value,
                buyerUid = order.buyerInfo.uid,
                buyerPhone = order.buyerInfo.phoneNumber?.value,
                buyerName = order.buyerInfo.userName,
                recipientInfo = recipientInfoPO,
                tradeStatus = order.tradeStatus,
                paymentStatus = order.paymentStatus,
                fulfillmentStatus = order.fulfillmentStatus,
                commitmentStatus = order.commitmentStatus,
                currency = order.amountSnapshot.currency,
                itemsSubtotal = order.amountSnapshot.itemsSubtotal.toBigDecimal(),
                discountAmount = order.amountSnapshot.discountAmount.toBigDecimal(),
                shippingAmount = order.amountSnapshot.shippingAmount.toBigDecimal(),
                taxAmount = order.amountSnapshot.taxAmount.toBigDecimal(),
                payableAmount = order.amountSnapshot.payableAmount.toBigDecimal(),
                paidAmount = order.paidAmount.toBigDecimal(),
                refundedAmount = order.refundedAmount.toBigDecimal(),
                paymentReference = order.paymentReference,
                fulfillmentReference = order.fulfillmentReference,
                createTime = order.createTime,
                updateTime = order.updateTime,
                items = order.items.map { toItemPO(it, order.id.value) }.toMutableList(),
                refundFacts =
                    order.successfulRefundFacts
                        .map {
                            com.jstore.order.domain.order.persistence.OrderRefundFactPO(
                                orderId = order.id.value,
                                refundId = it.refundId,
                                afterSaleId = it.afterSaleId.value,
                                orderItemId = it.orderItemId.value,
                                quantity = it.quantity,
                                amount = it.amount.toBigDecimal(),
                                occurredAt = it.occurredAt,
                            )
                        }
                        .toMutableList(),
            )
        }

        fun toItemPO(item: OrderItem, orderId: Long): OrderItemPO {
            return OrderItemPO(
                id = item.id.value,
                orderId = orderId,
                offerId = item.offerId,
                storeId = item.storeId,
                offerVersion = item.offerVersion,
                fulfillmentNodeId = item.fulfillmentNodeId,
                channelId = item.channelId,
                skuId = item.skuId,
                spuId = item.spuId,
                goodsName = item.goodsName,
                skuDescription = item.skuDescription,
                quantity = item.quantity,
                unitPrice = item.unitPrice.toBigDecimal(),
                snapshotVersion = item.snapshotVersion,
                status = item.status,
                refundedQuantity = item.refundedQuantity,
                refundedAmount = item.refundedAmount.toBigDecimal(),
            )
        }

        fun toDomain(po: OrderPO): Order {
            val items = po.items.map { toDomainItem(it) }.toMutableList()
            val recipientInfoPo = po.recipientInfo ?: error("Order ${po.id} has no consignee_info")

            val address =
                recipientInfoPo.shippingAddress
                    ?: error("Order ${po.id} consignee_info has no shippingAddress")

            val contractInfo =
                ContractInfo(
                    email = recipientInfoPo.consigneeEmail,
                    phoneNumber = recipientInfoPo.consigneePhone?.let { PhoneNumber(it) },
                )

            val consignInfo =
                RecipientInfo(
                    name = recipientInfoPo.consigneeName ?: "",
                    contractInfo = contractInfo,
                    shippingAddress = address,
                    shippingDetailAddress = recipientInfoPo.detailAddress,
                    postalCode = recipientInfoPo.postalCode,
                    customsFields = recipientInfoPo.customsFields ?: emptyMap(),
                )

            return OrderImpl(
                id = OrderId(po.id),
                merchantId = MerchantId(po.merchantId),
                buyerInfo =
                    UserInfo(
                        uid = po.buyerUid,
                        phoneNumber = po.buyerPhone?.let { PhoneNumber(it) },
                        userName = po.buyerName,
                    ),
                _items = items.toMutableList(),
                recipientInfo = consignInfo,
                _tradeStatus = po.tradeStatus,
                _paymentStatus = po.paymentStatus,
                _fulfillmentStatus = po.fulfillmentStatus,
                _commitmentStatus = po.commitmentStatus,
                amountSnapshot =
                    OrderAmountSnapshot(
                        currency = po.currency,
                        itemsSubtotal = Price.fromBigDecimal(po.itemsSubtotal),
                        discountAmount = Price.fromBigDecimal(po.discountAmount),
                        shippingAmount = Price.fromBigDecimal(po.shippingAmount),
                        taxAmount = Price.fromBigDecimal(po.taxAmount),
                        payableAmount = Price.fromBigDecimal(po.payableAmount),
                    ),
                _paidAmount = Price.fromBigDecimal(po.paidAmount),
                _refundedAmount = Price.fromBigDecimal(po.refundedAmount),
                _paymentReference = po.paymentReference,
                _fulfillmentReference = po.fulfillmentReference,
                refundFacts =
                    po.refundFacts
                        .map {
                            RefundFact(
                                it.refundId,
                                com.jstore.order.domain.aftersale.AfterSaleId(it.afterSaleId),
                                OrderItemId(it.orderItemId),
                                it.quantity,
                                Price.fromBigDecimal(it.amount),
                                it.occurredAt,
                            )
                        }
                        .toMutableList(),
                createTime = po.createTime,
                _updateTime = po.updateTime,
                sourceTradeId = po.sourceTradeId,
                sourceOrderPlanId = po.sourceOrderPlanId,
                sourcePlanDigest = po.sourcePlanDigest,
            )
        }

        fun toDomainItem(po: OrderItemPO): OrderItem {
            return OrderItemImpl(
                id = OrderItemId(po.id),
                skuId = po.skuId,
                spuId = po.spuId,
                // Rows written before the Offer boundary was introduced have no offer columns.
                // The migration backfills them; these fallbacks also keep history readers safe.
                offerId = po.offerId.takeIf { it > 0 } ?: po.skuId,
                storeId = po.storeId.takeIf { it > 0 } ?: 1,
                offerVersion = po.offerVersion.coerceAtLeast(1),
                fulfillmentNodeId = po.fulfillmentNodeId,
                channelId = po.channelId,
                goodsName = po.goodsName,
                skuDescription = po.skuDescription,
                quantity = po.quantity,
                unitPrice = Price.fromBigDecimal(po.unitPrice),
                snapshotVersion = po.snapshotVersion,
                status = po.status,
                _refundedQuantity = po.refundedQuantity,
                _refundedAmount = Price.fromBigDecimal(po.refundedAmount),
            )
        }
    }
}
