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
import com.jstore.common.utils.Failure
import com.jstore.order.acl.UserService
import com.jstore.order.domain.order.CancellationCategory
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.testOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OrderBuyerAuthorizationTest {
    private val factory = mock<OrderFactory>()
    private val repository = mock<OrderRepository>()
    private val publisher = mock<DomainEventPublisher>()
    private val users = mock<UserService>()
    private val service = OrderService(factory, repository, publisher, users)
    private val anotherBuyersOrder = testOrder()

    @Test
    fun `same numeric buyer id from another authentication domain cannot read order`() {
        whenever(repository.findById(anotherBuyersOrder.id)).thenReturn(anotherBuyersOrder)

        val result =
            service.getOrderById(
                "issuer-b",
                anotherBuyersOrder.buyerInfo.uid,
                anotherBuyersOrder.id,
            )

        assertEquals(Failure(OrderErrors.ORDER_NOT_FOUND), result)
    }

    @Test
    fun `buyer cannot read another buyers order`() {
        whenever(repository.findById(anotherBuyersOrder.id)).thenReturn(anotherBuyersOrder)

        val result = service.getOrderById("issuer-a", 42, anotherBuyersOrder.id)

        assertEquals(Failure(OrderErrors.ORDER_NOT_FOUND), result)
    }

    @Test
    fun `buyer cannot cancel another buyers order`() {
        whenever(repository.findById(anotherBuyersOrder.id)).thenReturn(anotherBuyersOrder)
        val command =
            OrderCancelCMD(
                anotherBuyersOrder.id,
                CancellationCategory.BUYER_CANCELLED,
                "not mine",
            )

        val result = service.cancelOrder("issuer-a", 42, command)

        assertEquals(Failure(OrderErrors.ORDER_NOT_FOUND), result)
        verify(repository, never()).save(anotherBuyersOrder)
    }
}
