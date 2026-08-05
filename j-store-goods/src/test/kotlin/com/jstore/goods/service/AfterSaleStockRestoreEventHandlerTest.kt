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
package com.jstore.goods.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.ContractItem
import com.jstore.contracts.commerce.RestoreInventoryAfterRefundCommand
import com.jstore.goods.domain.inventory.CommodityCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import org.mockito.Mockito.*

class AfterSaleStockRestoreEventHandlerTest :
    FunSpec({
        val command =
            RestoreInventoryAfterRefundCommand(
                afterSaleId = 1,
                orderId = 2,
                items = listOf(ContractItem(skuId = 3, quantity = 4)),
                sourceMessageId = "refund-1",
                occurredAtValue = Instant.parse("2026-08-05T00:00:00Z"),
            )

        test("restore contract preserves sku and exact quantity") {
            command.messageName shouldBe "inventory.restore-after-refund"
            command.items.single() shouldBe ContractItem(skuId = 3, quantity = 4)
        }
        test("handler adds the exact approved quantity once") {
            val inventory = mock(InventoryService::class.java)
            `when`(inventory.add(CommodityCode(3), BigDecimal(4))).thenReturn(Success(true))
            AfterSaleStockRestoreEventHandler(inventory).handle(command)
            verify(inventory, times(1)).add(CommodityCode(3), BigDecimal(4))
        }
        test("handler throws so event consumption is not acknowledged when inventory fails") {
            val inventory = mock(InventoryService::class.java)
            `when`(inventory.add(CommodityCode(3), BigDecimal(4)))
                .thenReturn(Failure(BusinessError("failed", "Inventory.Failed", 409)))
            shouldThrow<IllegalStateException> {
                AfterSaleStockRestoreEventHandler(inventory).handle(command)
            }
        }
    })
