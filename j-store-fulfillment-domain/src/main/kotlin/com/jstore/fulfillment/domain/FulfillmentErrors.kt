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
package com.jstore.fulfillment.domain

import com.jstore.common.errors.BusinessError

object FulfillmentErrors {
    val NOT_FOUND = BusinessError("履约单不存在", "Fulfillment.NotFound", 404)
    val ORDER_CONFLICT = BusinessError("订单履约快照冲突", "Fulfillment.Order.Conflict", 409)
    val INVALID_STATE = BusinessError("履约单状态不允许当前操作", "Fulfillment.State.Invalid", 409)
    val SHIPPING_REFERENCE_INVALID =
        BusinessError("承运商或运单号无效", "Fulfillment.ShippingReference.Invalid", 400)
    val SHIPPING_REFERENCE_CONFLICT =
        BusinessError("履约单已关联其他运单", "Fulfillment.ShippingReference.Conflict", 409)
}
