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
package com.jstore.fulfillment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.FulfillmentErrors
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.FulfillmentOrder
import com.jstore.fulfillment.domain.FulfillmentOrderId
import com.jstore.fulfillment.domain.FulfillmentOrderImpl
import com.jstore.fulfillment.domain.ShippingRecipient
import com.jstore.shop.api.MerchantCapability
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MerchantFulfillmentServiceTest {
    private val fulfillment =
        FulfillmentOrderImpl(
            FulfillmentOrderId(1),
            orderId = 9,
            merchantId = 7,
            recipient = ShippingRecipient("Ada", null, null, "JP", "13", "Tokyo"),
            items = listOf(FulfillmentItem(1, 2, 1)),
        )

    @Test
    fun `unauthorized mutation is hidden and never delegated`() {
        val fulfillments = StubFulfillmentUseCase(fulfillment)
        val service = MerchantFulfillmentService(fulfillments) { _, _, _ -> false }

        val result = service.prepare(accountId = 3, orderId = 9)

        assertEquals(FulfillmentErrors.NOT_FOUND, assertIs<Failure<*>>(result).error)
        assertEquals(0, fulfillments.prepareCalls)
    }

    @Test
    fun `authorized read checks the owning merchant and capability`() {
        val fulfillments = StubFulfillmentUseCase(fulfillment)
        val service =
            MerchantFulfillmentService(fulfillments) { accountId, merchantId, capability ->
                accountId == 3L &&
                    merchantId == 7L &&
                    capability == MerchantCapability.FULFILLMENT_READ
            }

        val result = service.get(accountId = 3, orderId = 9)

        assertEquals(fulfillment, assertIs<Success<FulfillmentOrder>>(result).value)
    }

    private class StubFulfillmentUseCase(private val fulfillment: FulfillmentOrder) :
        FulfillmentUseCase {
        var prepareCalls = 0
            private set

        override fun getByOrderId(orderId: Long): Result<FulfillmentOrder, BusinessError> =
            Success(fulfillment)

        override fun prepare(orderId: Long, occurredAt: Instant): Result<Boolean, BusinessError> {
            prepareCalls++
            return Success(true)
        }

        override fun createForOrder(
            request: FulfillmentRequest
        ): Result<FulfillmentOrder, BusinessError> = unsupported()

        override fun dispatch(
            orderId: Long,
            carrierCode: String,
            trackingNumber: String,
            occurredAt: Instant,
        ): Result<Boolean, BusinessError> = unsupported()

        override fun deliver(
            orderId: Long,
            occurredAt: Instant,
        ): Result<Boolean, BusinessError> = unsupported()

        private fun <T> unsupported(): Result<T, BusinessError> =
            throw UnsupportedOperationException()
    }
}
