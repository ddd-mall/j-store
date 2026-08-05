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
package com.jstore.translator

import com.jstore.common.framework.messaging.IntegrationMessage
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.common.properties.Price
import com.jstore.contracts.commerce.RestoreInventoryAfterRefundCommand
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.event.AfterSaleEventItem
import com.jstore.order.domain.aftersale.event.AfterSaleRefundSucceededEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AfterSaleRefundSucceededToStockRestoreTranslatorTest {
    @Test
    fun `successful refund publishes quantity-aware stock restore`() {
        val publisher = CapturingPublisher()
        val event =
            AfterSaleRefundSucceededEvent(
                AfterSaleId(1),
                OrderId(2),
                "refund-1",
                listOf(AfterSaleEventItem(OrderItemId(4), 5, 2, Price.ofFen(100), "CNY")),
                Price.ofFen(100),
                "CNY",
                Instant.EPOCH,
            )

        AfterSaleRefundSucceededToStockRestoreTranslator(publisher).onDomainEvent(event)

        val restored = publisher.messages.single() as RestoreInventoryAfterRefundCommand
        assertEquals(2, restored.items.single().quantity)
    }

    private class CapturingPublisher : IntegrationMessagePublisher {
        val messages = mutableListOf<IntegrationMessage>()

        override fun publish(message: IntegrationMessage) {
            messages += message
        }
    }
}
