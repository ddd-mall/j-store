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
package com.jstore.trade.controller

import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.utils.Success
import com.jstore.trade.service.CheckoutAccepted
import com.jstore.trade.service.CheckoutUseCase
import com.jstore.trade.service.CreateCheckoutCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CheckoutControllerContractTest {
    @Test
    fun `ready checkout response includes the payment action`() {
        val response = CheckoutController(CapturingCheckoutUseCase()).find(principal(42), 9001)
        val body = response.body as CheckoutController.CheckoutResponse

        assertEquals(200, response.statusCode.value())
        assertEquals(8001, body.payment?.paymentId)
        assertEquals("opaque-payment-action", body.payment?.payAction)
    }

    @Test
    fun `authenticated buyer is supplied by security context and response points to checkout`() {
        val useCase = CapturingCheckoutUseCase()
        val controller = CheckoutController(useCase)
        val request =
            CheckoutController.CreateCheckoutRequest(
                checkoutRequestId = "request-1",
                recipient =
                    CheckoutController.RecipientRequest(
                        name = "张三",
                        countryCode = "CN",
                        phone = "+8613800138000",
                        email = null,
                        districtCode = "110105",
                        detailAddress = "示例路 1 号",
                    ),
                items = listOf(CheckoutController.ItemRequest(11, 2, 21, 22, 1, 3)),
            )

        val response = controller.create(principal(42), request)
        val body = response.body as CheckoutController.CheckoutResponse

        assertEquals(202, response.statusCode.value())
        assertEquals(42, useCase.command?.buyerId)
        assertEquals("/api/checkouts/9001", body.statusUrl)
        assertFalse(request::class.members.any { it.name == "buyerId" })
        assertFalse(request::class.members.any { it.name == "merchantId" })
    }
}

private class CapturingCheckoutUseCase : CheckoutUseCase {
    var command: CreateCheckoutCommand? = null

    override fun checkout(command: CreateCheckoutCommand) =
        Success(CheckoutAccepted(9001, listOf(9001))).also { this.command = command }

    override fun find(buyerAuthenticationDomain: String, buyerId: Long, tradeId: Long) =
        Success(
            com.jstore.trade.service.CheckoutView(
                tradeId,
                "PAYMENT_READY",
                listOf(tradeId),
                com.jstore.trade.service.CheckoutPaymentView(
                    8001,
                    "READY",
                    1000,
                    "CNY",
                    "opaque-payment-action",
                    java.time.Instant.parse("2030-01-01T00:00:00Z"),
                ),
            )
        )
}

private fun principal(accountId: Long) =
    AuthenticatedPrincipal("issuer-a", AuthenticatedAccountId(accountId))
