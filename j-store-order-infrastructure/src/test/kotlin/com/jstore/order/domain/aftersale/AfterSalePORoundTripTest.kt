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
import com.jstore.order.domain.aftersale.persistence.*
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import java.time.LocalDateTime
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class AfterSalePORoundTripTest {
    @Test
    fun `persistence mapping preserves aggregate snapshots and review decision`() {
        val repository =
            AfterSaleRepositoryImpl(
                mock(AfterSalePOJpaRepository::class.java),
                mock(AfterSaleCapacityPOJpaRepository::class.java),
                mock(AfterSaleCommandReceiptPOJpaRepository::class.java),
                SnowFlakSequence(1, 1),
            )
        val now = LocalDateTime.of(2026, 8, 3, 10, 0)
        val goods = GoodsSnapshot(91, 81, "商品", "红色")
        val source =
            AfterSaleImpl(
                id = AfterSaleId(1),
                orderId = OrderId(2),
                applicantId = ApplicantActorId(3),
                merchantId = MerchantActorId(4),
                _status = AfterSaleStatus.REJECTED,
                reason = RefundReason(RefundCategory.OTHER, "不合适"),
                fulfillmentSnapshot = FulfillmentSnapshot(FulfillmentStatus.DELIVERED, true),
                items =
                    listOf(
                        AfterSaleItemImpl(
                            AfterSaleItemId(5),
                            OrderId(2),
                            OrderItemId(6),
                            2,
                            Price.ofFen(120),
                            "JPY",
                            RefundEligibilitySnapshot(
                                OrderItemId(6),
                                3,
                                Price.ofFen(200),
                                "JPY",
                                goods,
                            ),
                        )
                    ),
                _reviewDecision = ReviewDecision(MerchantActorId(4), now, "已使用"),
                createTime = now.minusDays(1),
                _updateTime = now,
                version = 7,
            )

        val restored = repository.toDomain(repository.toPO(source))

        assertEquals(source.id, restored.id)
        assertEquals(source.orderId, restored.orderId)
        assertEquals(source.status, restored.status)
        assertEquals(source.reason, restored.reason)
        assertEquals(source.fulfillmentSnapshot, restored.fulfillmentSnapshot)
        assertEquals(source.reviewDecision, restored.reviewDecision)
        assertEquals(source.items, restored.items)
        assertEquals(source.version, restored.version)
    }
}
