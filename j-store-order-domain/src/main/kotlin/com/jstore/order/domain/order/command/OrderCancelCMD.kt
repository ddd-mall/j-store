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
package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.CancellationCategory
import com.jstore.order.domain.order.CancellationReason
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderId

/** 取消订单命令 */
data class OrderCancelCMD(
    val orderId: OrderId,
    val category: CancellationCategory,
    val description: String,
) {
    fun validate(): Result<OrderCancelCMD, BusinessError> {
        if (description.isBlank()) return Failure(OrderErrors.CANCEL_REASON_INVALID)
        return Success(this)
    }

    fun toReason(): CancellationReason = CancellationReason(category, description)
}
