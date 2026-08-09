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
import com.jstore.common.utils.Result
import com.jstore.fulfillment.domain.FulfillmentOrder
import java.time.Instant

interface FulfillmentUseCase {
    fun createForOrder(request: FulfillmentRequest): Result<FulfillmentOrder, BusinessError>

    fun getByOrderId(orderId: Long): Result<FulfillmentOrder, BusinessError>

    fun prepare(orderId: Long, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>

    fun dispatch(
        orderId: Long,
        carrierCode: String,
        trackingNumber: String,
        occurredAt: Instant = Instant.now(),
    ): Result<Boolean, BusinessError>

    fun deliver(orderId: Long, occurredAt: Instant = Instant.now()): Result<Boolean, BusinessError>
}
