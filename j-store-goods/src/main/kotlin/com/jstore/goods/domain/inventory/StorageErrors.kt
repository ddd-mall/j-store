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
package com.jstore.goods.domain.inventory

import com.jstore.common.errors.BusinessError

object StorageErrors {
    val INSUFFICIENT_INVENTORY = BusinessError("库存不足", "business.insufficientInventory", 400)
    val INVALID_AMOUNT = BusinessError("库存参数错误", "business.invalidAmount", 400)
    val STORAGE_DOSE_NOT_EXIST = BusinessError("库存不存在", "business.storageDoesNotExist", 404)
    val STORAGE_OPERATION_FAILED = BusinessError("库存操作失败", "business.storageOperationFailed", 500)
    val RESERVATION_RECORD_NOT_FOUND =
        BusinessError("未能找到预扣记录", "business.reservationRecordNotFound", 404)
}
