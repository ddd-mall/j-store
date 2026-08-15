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
package com.jstore.payment.service

import com.jstore.common.errors.BusinessErrorException
import com.jstore.common.properties.Price
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.RequestPaymentRefundCommand
import com.jstore.messaging.IntegrationMessageHandler
import com.jstore.payment.domain.payment.PaymentRefundItem

class RequestPaymentRefundCommandHandler(private val payments: PaymentUseCase) :
    IntegrationMessageHandler<RequestPaymentRefundCommand> {
    override fun handlerId() = "payment.request-refund.v1"

    override fun handle(message: RequestPaymentRefundCommand) {
        payments
            .requestRefund(
                PaymentRefundRequest(
                    message.orderId,
                    message.afterSaleId,
                    message.items.map {
                        PaymentRefundItem(
                            it.orderItemId,
                            it.skuId,
                            it.quantity,
                            Price.ofFen(it.amountFen),
                        )
                    },
                    Price.ofFen(message.amountFen),
                ),
                message.occurredAt,
            )
            .getOrThrow(::BusinessErrorException)
    }
}
