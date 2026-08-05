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
package com.jstore.order.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.order.config.OrderBootConfiguration
import com.jstore.order.domain.aftersale.AfterSaleRepository
import com.jstore.order.domain.order.OrderRepository
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class AfterSaleBootWiringTest {
    @Test
    fun `configuration exposes after-sale factory and application service`() {
        val configuration = OrderBootConfiguration()
        val sequence = configuration.snowFlakSequence()
        val factory = configuration.afterSaleFactory(sequence)
        val repository = mock(AfterSaleRepository::class.java)
        val orders = mock(OrderRepository::class.java)
        assertNotNull(
            configuration.afterSaleApplicationService(
                factory,
                repository,
                orders,
                mock(DomainEventPublisher::class.java),
            )
        )
    }
}
