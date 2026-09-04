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
package com.jstore.cart.controller

import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.cart.service.*
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.PutMapping

class CartControllerContractTest {
    @Test
    fun `quantity endpoint maps authenticated buyer and absolute target command`() {
        val useCase = CapturingCartUseCase()
        val controller = CartController(useCase)

        controller.setItemQuantity(
            user = AuthenticatedPrincipal("local", AuthenticatedAccountId(7)),
            request =
                CartController.SetItemQuantityRequest(
                    skuId = 101,
                    offerId = 201,
                    targetQuantity = 3,
                    expectedCartVersion = 12,
                ),
        )

        assertEquals(
            SetCartItemQuantityCommand(
                buyerId = 7,
                skuId = 101,
                offerId = 201,
                targetQuantity = 3,
                expectedCartVersion = 12,
            ),
            useCase.quantityCommand,
        )
        val mapping =
            CartController::class
                .java
                .getDeclaredMethod(
                    "setItemQuantity",
                    AuthenticatedPrincipal::class.java,
                    CartController.SetItemQuantityRequest::class.java,
                )
                .getAnnotation(PutMapping::class.java)
        assertArrayEquals(arrayOf("/items"), mapping.value)
    }

    private class CapturingCartUseCase : CartUseCase {
        var quantityCommand: SetCartItemQuantityCommand? = null
        var quantityCalls = 0

        override fun setItemQuantity(
            command: SetCartItemQuantityCommand
        ): Result<CartView, BusinessError> {
            quantityCalls++
            quantityCommand = command
            return Success(view())
        }

        override fun replaceSelection(
            command: ReplaceCartSelectionCommand
        ): Result<CartView, BusinessError> = Success(view())

        override fun refresh(
            buyerId: Long,
            expectedVersion: Long,
        ): Result<CartView, BusinessError> = Success(view())

        override fun current(buyerId: Long): Result<CartView, BusinessError> = Success(view())

        private fun view() =
            CartView(
                cartId = 1,
                contentVersion = 1,
                market = "CN",
                channelId = "ONLINE",
                currency = "CNY",
                lines = emptyList(),
                assessment = null,
            )
    }
}
