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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.cart.domain.BuyerId
import com.jstore.cart.domain.CartId
import com.jstore.cart.domain.CartRefreshReason
import com.jstore.cart.domain.CartRefreshRequestedEvent
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

    @Test
    fun `assessment response status retains its JSON values`() {
        val mapper = ObjectMapper().findAndRegisterModules()
        for (status in CartAssessmentViewStatus.entries) {
            val view = CartAssessmentView(1, status, 0, "CNY", emptyList())
            assertEquals(
                status.name,
                mapper.readTree(mapper.writeValueAsString(view))["status"].asText(),
            )
        }
    }

    @Test
    fun `refresh event reason survives JSON round trip`() {
        val mapper =
            ObjectMapper()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        for (reason in CartRefreshReason.entries) {
            val event =
                CartRefreshRequestedEvent(
                    CartId(1),
                    BuyerId(7),
                    1,
                    reason,
                )
            val json = mapper.writeValueAsString(event)
            assertEquals(reason.name, mapper.readTree(json)["reason"].asText())
            assertEquals(
                event,
                mapper.readValue(
                    json,
                    CartRefreshRequestedEvent::class.java,
                ),
            )
        }
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
