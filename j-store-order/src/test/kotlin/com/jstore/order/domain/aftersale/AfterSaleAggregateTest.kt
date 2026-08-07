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
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.*

class AfterSaleAggregateTest :
    FunSpec({
        val snapshot =
            RefundEligibilitySnapshot(
                OrderItemId(10),
                2,
                Price.ofFen(200),
                "CNY",
                GoodsSnapshot(1, 2, "g", "s"),
            )
        fun aggregate() =
            AfterSaleImpl(
                AfterSaleId(1),
                OrderId(2),
                ApplicantActorId(3),
                MerchantActorId(4),
                AfterSaleStatus.REQUESTED,
                RefundReason(RefundCategory.OTHER, "reason"),
                FulfillmentSnapshot(FulfillmentStatus.UNFULFILLED, false),
                listOf(
                    AfterSaleItemImpl(
                        AfterSaleItemId(5),
                        OrderId(2),
                        OrderItemId(10),
                        1,
                        Price.ofFen(100),
                        "CNY",
                        snapshot,
                    )
                ),
                createTime = LocalDateTime.MIN,
                _updateTime = LocalDateTime.MIN,
            )
        test("value objects reject invalid bounds") {
            shouldThrow<IllegalArgumentException> { RefundReason(RefundCategory.OTHER, " ") }
            shouldThrow<IllegalArgumentException> {
                RefundEligibilitySnapshot(OrderItemId(1), 0, Price.ZERO, "CNY", snapshot.goods)
            }
        }
        test("aggregate rejects empty duplicate and cross-order items") {
            shouldThrow<IllegalArgumentException> {
                AfterSaleImpl(
                    AfterSaleId(1),
                    OrderId(2),
                    ApplicantActorId(3),
                    MerchantActorId(4),
                    AfterSaleStatus.REQUESTED,
                    RefundReason(RefundCategory.OTHER, "r"),
                    FulfillmentSnapshot(FulfillmentStatus.UNFULFILLED, false),
                    emptyList(),
                    createTime = LocalDateTime.MIN,
                    _updateTime = LocalDateTime.MIN,
                )
            }
        }
        test("approval moves an unshipped after-sale to refund pending") {
            val a = aggregate()
            (a.approve(MerchantActorId(99), Instant.EPOCH) is Failure) shouldBe true
            a.domainEventQueue.size shouldBe 0
            (a.approve(MerchantActorId(4), Instant.EPOCH) is Success) shouldBe true
            a.status shouldBe AfterSaleStatus.REFUND_PENDING
            a.domainEventQueue.size shouldBe 2
            (a.cancel(ApplicantActorId(3), Instant.EPOCH) is Failure) shouldBe true
            a.domainEventQueue.size shouldBe 2
        }
    })
