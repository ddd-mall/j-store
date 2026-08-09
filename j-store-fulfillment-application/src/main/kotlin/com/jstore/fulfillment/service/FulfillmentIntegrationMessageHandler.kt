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
package com.jstore.fulfillment.service

import com.jstore.common.errors.BusinessErrorException
import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.CreateFulfillmentForOrderCommand
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.ShippingRecipient

class CreateFulfillmentForOrderCommandHandler(private val fulfillments: FulfillmentUseCase) :
    IntegrationMessageHandler<CreateFulfillmentForOrderCommand> {
    override fun handlerId() = "fulfillment.create-for-order.v1"

    override fun handle(message: CreateFulfillmentForOrderCommand) {
        fulfillments
            .createForOrder(
                FulfillmentRequest(
                    orderId = message.orderId,
                    merchantId = message.merchantId,
                    recipient =
                        ShippingRecipient(
                            message.recipient.name,
                            message.recipient.phone,
                            message.recipient.email,
                            message.recipient.countryCode,
                            message.recipient.districtCode,
                            message.recipient.detailAddress,
                        ),
                    items =
                        message.items.map {
                            FulfillmentItem(
                                requireNotNull(it.orderItemId),
                                it.skuId,
                                it.quantity,
                            )
                        },
                )
            )
            .getOrThrow(::BusinessErrorException)
    }
}
