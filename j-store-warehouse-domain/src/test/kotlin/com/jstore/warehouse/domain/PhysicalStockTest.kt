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
package com.jstore.warehouse.domain

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.warehouse.domain.event.PhysicalStockChangedEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PhysicalStockTest {
    @Test
    fun `physical adjustment publishes a monotonically versioned warehouse fact`() {
        val stock = PhysicalStock(PhysicalStockId("11@CN-NORTH-1"), 11, "CN-NORTH-1", 10, 3)

        assertIs<Success<Unit>>(stock.adjustTo(12, "cycle-count"))
        assertEquals(4, stock.sourceVersion)
        assertEquals(12, stock.onHand)
        assertEquals(
            4,
            (stock.pendingDomainEvents().single() as PhysicalStockChangedEvent).sourceVersion,
        )
        assertIs<Failure<*>>(stock.adjustTo(-1, "invalid"))
    }
}
