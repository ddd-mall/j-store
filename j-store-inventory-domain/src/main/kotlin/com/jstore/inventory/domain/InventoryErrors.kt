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
package com.jstore.inventory.domain

import com.jstore.common.errors.BusinessError

object InventoryErrors {
    val POSITION_NOT_FOUND = BusinessError("库存位置不存在", "Inventory.Position.NotFound", 404)
    val INSUFFICIENT_ATP = BusinessError("可承诺库存不足", "Inventory.Atp.Insufficient", 409)
    val INVALID_QUANTITY = BusinessError("库存数量无效", "Inventory.Quantity.Invalid", 400)
    val RESERVATION_NOT_FOUND = BusinessError("库存预留不存在", "Inventory.Reservation.NotFound", 404)
    val RESERVATION_CONFLICT = BusinessError("库存预留业务键冲突", "Inventory.Reservation.Conflict", 409)
    val ILLEGAL_RESERVATION_STATE =
        BusinessError("库存预留状态不允许该操作", "Inventory.Reservation.IllegalState", 409)
}
