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
package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.query.Page
import com.jstore.common.query.SortedPage
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.acl.UserService
import com.jstore.order.domain.order.*
import com.jstore.order.domain.order.event.OrderCancelledByTradeEvent
import com.jstore.order.domain.order.event.OrderCancelledEvent
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TradeOrderCreationIdempotencyTest {
    @Test
    fun `same trade plan returns original order without rebuilding it`() {
        val sequence = mock<SnowFlakSequence>()
        whenever(sequence.nextId()).thenReturn(101, 1001)
        val repository = InMemoryTradeOrderRepository()
        val service = service(repository, TrustedOrderFactoryImpl(sequence))
        val command = command()

        val first = assertIs<Success<Order>>(service.createOrder(command)).value
        val duplicate = assertIs<Success<Order>>(service.createOrder(command)).value

        assertEquals(1001, first.id.value)
        assertEquals(first.id, duplicate.id)
        assertEquals(1, repository.additions)
        assertEquals(9001, first.sourceTradeId)
        assertEquals(9101, first.sourceOrderPlanId)
    }

    @Test
    fun `same order plan with a different source digest is rejected`() {
        val sequence = mock<SnowFlakSequence>()
        whenever(sequence.nextId()).thenReturn(101, 1001)
        val repository = InMemoryTradeOrderRepository()
        val service = service(repository, TrustedOrderFactoryImpl(sequence))
        assertIs<Success<Order>>(service.createOrder(command()))

        val conflict = service.createOrder(command().copy(planDigest = "v1:changed"))

        assertEquals(
            "Order.TradePlan.Conflict",
            assertIs<Failure<*>>(conflict)
                .error
                .let { it as com.jstore.common.errors.BusinessError }
                .errorCode,
        )
        assertEquals(1, repository.additions)
    }

    @Test
    fun `trade compensation closes order without publishing buyer cancellation`() {
        val sequence = mock<SnowFlakSequence>()
        whenever(sequence.nextId()).thenReturn(101, 1001)
        val repository = InMemoryTradeOrderRepository()
        val events = mutableListOf<com.jstore.common.framework.event.DomainEvent>()
        val service =
            service(
                repository,
                TrustedOrderFactoryImpl(sequence),
                object : DomainEventPublisher {
                    override fun publishEvent(
                        event: com.jstore.common.framework.event.DomainEvent
                    ) {
                        events += event
                    }
                },
            )
        assertIs<Success<Order>>(service.createOrder(command()))

        assertIs<Success<Unit>>(service.cancelOrder(9101, "trade compensation"))

        assertEquals(1, events.filterIsInstance<OrderCancelledByTradeEvent>().size)
        assertEquals(0, events.filterIsInstance<OrderCancelledEvent>().size)
        assertEquals(TradeStatus.CLOSED, repository.findBySourceOrderPlanId(9101)?.tradeStatus)
    }

    private fun service(
        repository: OrderRepository,
        factory: TrustedOrderFactory,
        publisher: DomainEventPublisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: com.jstore.common.framework.event.DomainEvent) =
                    Unit
            },
    ) =
        OrderService(
            mock(),
            repository,
            publisher,
            mock<UserService>(),
            factory,
        )

    private fun command() =
        CreateOrderFromTradeCommand(
            tradeId = 9001,
            orderPlanId = 9101,
            planDigest = "v1:plan",
            merchantId = 7,
            buyerId = 42,
            buyerName = "张三",
            buyerPhone = "+8613800138000",
            recipientName = "张三",
            recipientPhone = "+8613800138000",
            recipientEmail = null,
            shippingAddress =
                I18nGeoAddress(
                    CountryCode.CN,
                    listOf(
                        AddressComponent(
                            "110105",
                            DivisionLevel(3, "district"),
                            mapOf(Locale.CHINA to "朝阳区"),
                            Locale.CHINA,
                        )
                    ),
                ),
            detailAddress = "示例路 1 号",
            postalCode = null,
            customsFields = emptyMap(),
            items =
                listOf(
                    CreateOrderFromTradeItem(
                        201,
                        101,
                        11,
                        71,
                        1,
                        "NODE-1",
                        "WEB",
                        "商品",
                        "规格",
                        1,
                        Price.ofFen(1000),
                        1,
                    )
                ),
            payableAmount = Price.ofFen(1000),
            currency = "CNY",
        )
}

private class InMemoryTradeOrderRepository : OrderRepository {
    private val values = linkedMapOf<OrderId, Order>()
    var additions = 0

    override fun add(order: Order) {
        additions++
        values[order.id] = order
    }

    override fun save(entity: Order): Order = entity.also { values[it.id] = it }

    override fun findById(id: OrderId): Order? = values[id]

    override fun findByBuyerUserId(uid: Long): List<Order> =
        values.values.filter { it.buyerInfo.uid == uid }

    override fun findBySourceOrderPlanId(orderPlanId: Long): Order? =
        values.values.singleOrNull { it.sourceOrderPlanId == orderPlanId }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> =
        SortedPage(currentPage, 0, emptyList())
}
