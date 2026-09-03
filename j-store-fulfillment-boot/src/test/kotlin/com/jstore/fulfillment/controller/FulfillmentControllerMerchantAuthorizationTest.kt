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
package com.jstore.fulfillment.controller

import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.FulfillmentErrors
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.FulfillmentOrderId
import com.jstore.fulfillment.domain.FulfillmentOrderImpl
import com.jstore.fulfillment.domain.ShippingRecipient
import com.jstore.fulfillment.service.MerchantFulfillmentUseCase
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.http.HttpStatus

class FulfillmentControllerMerchantAuthorizationTest {
    @Test
    fun `order manager can access fulfillment while numerically equal non-member cannot`() {
        val service = mock(MerchantFulfillmentUseCase::class.java)
        val order =
            FulfillmentOrderImpl(
                FulfillmentOrderId(1),
                orderId = 9,
                merchantId = 70,
                recipient = ShippingRecipient("张三", null, null, "CN", "310000", null),
                items = listOf(FulfillmentItem(1, 2, 1)),
            )
        `when`(service.get(900, 9)).thenReturn(Success(order))
        `when`(service.get(70, 9)).thenReturn(Failure(FulfillmentErrors.NOT_FOUND))
        val controller = FulfillmentController(service)

        assertEquals(HttpStatus.OK, controller.get(principal(900), 9).statusCode)
        assertEquals(HttpStatus.NOT_FOUND, controller.get(principal(70), 9).statusCode)
    }
}

private fun principal(accountId: Long) =
    AuthenticatedPrincipal("issuer-a", AuthenticatedAccountId(accountId))
