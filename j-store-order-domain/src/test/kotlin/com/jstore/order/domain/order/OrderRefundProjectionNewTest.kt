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

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.AfterSaleId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class OrderRefundProjectionNewTest :
    FunSpec({
        test("successful refund is partial, idempotent and leaves fulfillment facts unchanged") {
            val order =
                testOrder(
                    trade = TradeStatus.ACTIVE,
                    payment = PaymentStatus.PAID,
                    fulfillment = FulfillmentStatus.DELIVERED,
                    itemStatuses = listOf(OrderItemStatus.SHIPPING_FINISHED),
                )
            val before = order.fulfillmentStatus
            val first =
                order.recordRefundSucceeded(
                    "refund-9",
                    AfterSaleId(9),
                    listOf(SuccessfulRefundItem(OrderItemId(1), 1, Price.ofFen(50))),
                    Instant.EPOCH,
                ) as Success
            first.value.newlyRegistered shouldBe true
            order.paymentStatus shouldBe PaymentStatus.PARTIALLY_REFUNDED
            order.fulfillmentStatus shouldBe before
            order.items.single().status shouldBe OrderItemStatus.SHIPPING_FINISHED
            val duplicate =
                order.recordRefundSucceeded(
                    "refund-9",
                    AfterSaleId(9),
                    listOf(SuccessfulRefundItem(OrderItemId(1), 1, Price.ofFen(50))),
                    Instant.EPOCH,
                ) as Success
            duplicate.value.newlyRegistered shouldBe false
            order.refundedAmount shouldBe Price.ofFen(50)
        }

        test("invalid refund fact is atomic") {
            val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
            (order.recordRefundSucceeded(
                "refund-10",
                AfterSaleId(10),
                listOf(SuccessfulRefundItem(OrderItemId(1), 2, Price.ofFen(200))),
                Instant.EPOCH,
            ) is Failure) shouldBe true
            order.refundedAmount shouldBe Price.ZERO
        }
    })
