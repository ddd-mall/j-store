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
import com.jstore.order.domain.order.event.OrderCancelledEvent
import com.jstore.order.domain.order.event.OrderTradeCommittedEvent
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class OrderLifecycleRegressionTest {
    @Test
    fun `trade commitment activates an order exactly once`() {
        val order = testOrder(commitment = CommitmentStatus.PENDING_OFFER)

        assertIs<Success<Unit>>(order.confirmTradeCommitment())
        assertEquals(CommitmentStatus.CONFIRMED, order.commitmentStatus)
        assertEquals(TradeStatus.ACTIVE, order.tradeStatus)
        assertIs<Failure<*>>(order.confirmTradeCommitment())
    }

    @Test
    fun `stock confirmation activates order and records payment eligibility fact once`() {
        val order = testOrder(trade = TradeStatus.CREATED)

        assertIs<Success<Unit>>(order.confirmTradeCommitment())

        assertEquals(TradeStatus.ACTIVE, order.tradeStatus)
        val event =
            order.pendingDomainEvents().filterIsInstance<OrderTradeCommittedEvent>().single()
        assertEquals(order.id, event.orderId)
        assertEquals(order.merchantId, event.merchantId)
        assertEquals(order.amountSnapshot.payableAmount, event.payableAmount)
        assertEquals(order.amountSnapshot.currency, event.currency)
        assertIs<Failure<*>>(order.confirmTradeCommitment())
        assertEquals(
            1,
            order.pendingDomainEvents().filterIsInstance<OrderTradeCommittedEvent>().size,
        )
    }

    @Test
    fun `trade commitment failure closes order without recording payment eligibility fact`() {
        val order = testOrder(trade = TradeStatus.CREATED)

        assertIs<Success<Unit>>(order.rejectTradeCommitment("out of stock"))

        assertEquals(CommitmentStatus.FAILED, order.commitmentStatus)
        assertEquals(TradeStatus.CLOSED, order.tradeStatus)
        assertIs<OrderCancelledEvent>(
            order.pendingDomainEvents().filterIsInstance<OrderCancelledEvent>().single()
        )
        assertTrue(order.pendingDomainEvents().none { it is OrderTradeCommittedEvent })
    }

    @Test
    fun `paid order preserves fulfillment sequence through delivery and completion`() {
        val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
        assertIs<Success<Boolean>>(order.recordFulfillmentPrepared("fulfillment-1"))
        assertIs<Success<Boolean>>(order.recordShipmentDispatched("fulfillment-1"))
        assertEquals(FulfillmentStatus.SHIPPED, order.fulfillmentStatus)
        assertEquals(OrderItemStatus.SHIPPING, order.items.single().status)
        assertIs<Success<Boolean>>(order.recordShipmentDelivered("fulfillment-1"))
        assertIs<Success<Unit>>(order.complete())
        assertEquals(TradeStatus.COMPLETED, order.tradeStatus)
        assertEquals(OrderItemStatus.SHIPPING_FINISHED, order.items.single().status)
    }

    @Test
    fun `unpaid cancellation closes transaction but paid cancellation is atomic failure`() {
        val unpaid = testOrder(trade = TradeStatus.ACTIVE)
        assertIs<Success<Unit>>(
            unpaid.cancel(CancellationReason(CancellationCategory.BUYER_CANCELLED, "buyer"))
        )
        assertEquals(TradeStatus.CLOSED, unpaid.tradeStatus)
        assertEquals(OrderItemStatus.CANCELED, unpaid.items.single().status)

        val paid = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
        val before = Triple(paid.tradeStatus, paid.paymentStatus, paid.items.single().status)
        assertIs<Failure<*>>(
            paid.cancel(CancellationReason(CancellationCategory.BUYER_CANCELLED, "late"))
        )
        assertEquals(
            before,
            Triple(paid.tradeStatus, paid.paymentStatus, paid.items.single().status),
        )
    }

    @Test
    fun `invalid payment does not partially mutate amount or statuses`() {
        val order = testOrder(trade = TradeStatus.CREATED)
        val before = Triple(order.tradeStatus, order.paymentStatus, order.paidAmount)
        assertIs<Failure<*>>(
            order.recordPaymentCaptured(
                "payment-1",
                Price.ofFen(100),
                "CNY",
                java.time.Instant.EPOCH,
            )
        )
        assertEquals(before, Triple(order.tradeStatus, order.paymentStatus, order.paidAmount))
    }
}
