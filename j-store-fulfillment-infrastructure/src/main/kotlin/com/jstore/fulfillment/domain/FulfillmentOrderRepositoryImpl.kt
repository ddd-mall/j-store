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
package com.jstore.fulfillment.domain

import com.jstore.fulfillment.domain.persistence.FulfillmentItemPO
import com.jstore.fulfillment.domain.persistence.FulfillmentOrderPO
import com.jstore.fulfillment.domain.persistence.FulfillmentOrderPOJpaRepository
import org.springframework.stereotype.Repository

@Repository
class FulfillmentOrderRepositoryImpl(private val jpaRepository: FulfillmentOrderPOJpaRepository) :
    FulfillmentOrderRepository {
    override fun save(entity: FulfillmentOrder): FulfillmentOrder =
        toDomain(jpaRepository.save(toPO(entity)))

    override fun findById(id: FulfillmentOrderId): FulfillmentOrder? =
        jpaRepository.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByOrderId(orderId: Long): FulfillmentOrder? =
        jpaRepository.findByOrderId(orderId)?.let(::toDomain)

    private fun toPO(fulfillment: FulfillmentOrder) =
        FulfillmentOrderPO(
            id = fulfillment.id.value,
            orderId = fulfillment.orderId,
            merchantId = fulfillment.merchantId,
            status = fulfillment.status,
            recipientName = fulfillment.recipient.name,
            recipientPhone = fulfillment.recipient.phone,
            recipientEmail = fulfillment.recipient.email,
            countryCode = fulfillment.recipient.countryCode,
            districtCode = fulfillment.recipient.districtCode,
            detailAddress = fulfillment.recipient.detailAddress,
            carrierCode = fulfillment.carrierCode,
            trackingNumber = fulfillment.trackingNumber,
            items =
                fulfillment.items
                    .map {
                        FulfillmentItemPO(
                            id = it.orderItemId,
                            fulfillmentOrderId = fulfillment.id.value,
                            orderItemId = it.orderItemId,
                            skuId = it.skuId,
                            quantity = it.quantity,
                        )
                    }
                    .toMutableList(),
        )

    private fun toDomain(po: FulfillmentOrderPO): FulfillmentOrder =
        FulfillmentOrderImpl(
            id = FulfillmentOrderId(po.id),
            orderId = po.orderId,
            merchantId = po.merchantId,
            recipient =
                ShippingRecipient(
                    po.recipientName,
                    po.recipientPhone,
                    po.recipientEmail,
                    po.countryCode,
                    po.districtCode,
                    po.detailAddress,
                ),
            items = po.items.map { FulfillmentItem(it.orderItemId, it.skuId, it.quantity) },
            _status = po.status,
            _carrierCode = po.carrierCode,
            _trackingNumber = po.trackingNumber,
        )
}
