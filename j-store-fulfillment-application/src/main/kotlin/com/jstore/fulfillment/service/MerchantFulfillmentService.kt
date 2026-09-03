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
import com.jstore.fulfillment.domain.FulfillmentOrder
import com.jstore.shop.api.MerchantAuthorizationQuery
import com.jstore.shop.api.MerchantCapability

interface MerchantFulfillmentUseCase {
    fun get(accountId: Long, orderId: Long): Result<FulfillmentOrder, BusinessError>

    fun prepare(accountId: Long, orderId: Long): Result<Boolean, BusinessError>

    fun dispatch(
        accountId: Long,
        orderId: Long,
        carrierCode: String,
        trackingNumber: String,
    ): Result<Boolean, BusinessError>

    fun deliver(accountId: Long, orderId: Long): Result<Boolean, BusinessError>
}

class MerchantFulfillmentService(
    private val fulfillments: FulfillmentUseCase,
    private val authorization: MerchantAuthorizationQuery,
) : MerchantFulfillmentUseCase {
    override fun get(accountId: Long, orderId: Long): Result<FulfillmentOrder, BusinessError> =
        authorized(accountId, orderId, MerchantCapability.FULFILLMENT_READ)

    override fun prepare(accountId: Long, orderId: Long): Result<Boolean, BusinessError> =
        mutate(accountId, orderId) { fulfillments.prepare(orderId) }

    override fun dispatch(
        accountId: Long,
        orderId: Long,
        carrierCode: String,
        trackingNumber: String,
    ): Result<Boolean, BusinessError> =
        mutate(accountId, orderId) {
            fulfillments.dispatch(orderId, carrierCode, trackingNumber)
        }

    override fun deliver(accountId: Long, orderId: Long): Result<Boolean, BusinessError> =
        mutate(accountId, orderId) { fulfillments.deliver(orderId) }

    private fun mutate(
        accountId: Long,
        orderId: Long,
        operation: () -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> =
        when (authorized(accountId, orderId, MerchantCapability.FULFILLMENT_MANAGE)) {
            is Success -> operation()
            is Failure -> Failure(FulfillmentErrors.NOT_FOUND)
        }

    private fun authorized(
        accountId: Long,
        orderId: Long,
        capability: MerchantCapability,
    ): Result<FulfillmentOrder, BusinessError> {
        val fulfillment =
            when (val result = fulfillments.getByOrderId(orderId)) {
                is Success -> result.value
                is Failure -> return result
            }
        return if (authorization.isAllowed(accountId, fulfillment.merchantId, capability))
            Success(fulfillment)
        else Failure(FulfillmentErrors.NOT_FOUND)
    }
}
