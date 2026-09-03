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
package com.jstore.payment.controller

import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.currency.SiteCurrencyPolicy
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.PaymentErrors
import com.jstore.payment.domain.payment.PaymentOrderId
import com.jstore.payment.domain.payment.PaymentOrderImpl
import com.jstore.payment.service.MerchantPaymentUseCase
import com.jstore.payment.service.PaymentCaptureCommand
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.http.HttpStatus

class PaymentControllerMerchantAuthorizationTest {
    @Test
    fun `finance member can access payment while numerically equal non-member cannot`() {
        val service = mock(MerchantPaymentUseCase::class.java)
        val order = PaymentOrderImpl(PaymentOrderId(1), 9, 70, Price.ofFen(100), "CNY")
        `when`(service.get(900, 9)).thenReturn(Success(order))
        `when`(service.get(70, 9)).thenReturn(Failure(PaymentErrors.ORDER_NOT_FOUND))
        val controller =
            PaymentController(
                service,
                SiteCurrencyPolicy("CNY", setOf("CNY")),
            )

        assertEquals(HttpStatus.OK, controller.get(principal(900), 9).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.get(principal(70), 9).statusCode)
    }

    @Test
    fun `capture uses site default and rejects currencies outside the site policy`() {
        val service = mock(MerchantPaymentUseCase::class.java)
        `when`(
                service.capture(
                    eq(900L),
                    eq(PaymentCaptureCommand(9, "txn-1", Price.ofFen(100), "JPY")),
                )
            )
            .thenReturn(Success(true))

        val controller =
            PaymentController(
                service,
                SiteCurrencyPolicy("JPY", setOf("JPY", "USD")),
            )

        assertEquals(
            HttpStatus.OK,
            controller
                .capture(
                    principal(900),
                    9,
                    PaymentController.CaptureRequest("txn-1", 100),
                )
                .statusCode,
        )
        assertEquals(
            HttpStatus.BAD_REQUEST,
            controller
                .capture(
                    principal(900),
                    9,
                    PaymentController.CaptureRequest("txn-2", 100, "CNY"),
                )
                .statusCode,
        )
        verify(service)
            .capture(
                eq(900L),
                eq(PaymentCaptureCommand(9, "txn-1", Price.ofFen(100), "JPY")),
            )
        verify(service, never())
            .capture(
                eq(900L),
                eq(PaymentCaptureCommand(9, "txn-2", Price.ofFen(100), "CNY")),
            )
    }
}

private fun principal(accountId: Long) =
    AuthenticatedPrincipal("issuer-a", AuthenticatedAccountId(accountId))
