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
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class OrderServiceStatusDimensionsTest :
    FunSpec({
        test("domain failure is propagated and aggregate is not saved") {
            val factory = mock(OrderFactory::class.java)
            val repository = mock(OrderRepository::class.java)
            val publisher = mock(DomainEventPublisher::class.java)
            val order = mock(Order::class.java)
            val id = OrderId(1)
            `when`(repository.findById(id)).thenReturn(order)
            `when`(order.confirmStock()).thenReturn(Failure(OrderErrors.ILLEGAL_STATE))

            OrderService(factory, repository, publisher)
                .confirmStock(id)
                .shouldBeInstanceOf<Failure<*>>()
            verify(repository, never()).save(order)
        }
    })
