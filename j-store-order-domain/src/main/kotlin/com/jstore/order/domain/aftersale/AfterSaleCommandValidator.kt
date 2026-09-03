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
package com.jstore.order.domain.aftersale

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.command.AfterSaleApproveCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCancelCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRejectCMD

/** Validates and normalizes after-sale commands while keeping commands behavior-free. */
object AfterSaleCommandValidator {
    fun validate(command: AfterSaleCreateCMD): Result<AfterSaleCreateCMD, BusinessError> {
        if (!validKey(command.idempotencyKey))
            return Failure(AfterSaleErrors.IDEMPOTENCY_KEY_INVALID)
        if (command.items.isEmpty() || command.items.size > 100)
            return Failure(AfterSaleErrors.ITEMS_EMPTY)
        if (command.items.map { it.orderItemId }.toSet().size != command.items.size)
            return Failure(AfterSaleErrors.ITEM_DUPLICATED)
        if (command.items.any { it.quantity <= 0 }) return Failure(AfterSaleErrors.QUANTITY_INVALID)
        if (command.items.any { it.amount <= Price.ZERO })
            return Failure(AfterSaleErrors.AMOUNT_INVALID)
        return Success(command.copy(idempotencyKey = command.idempotencyKey.trim()))
    }

    fun validate(command: AfterSaleApproveCMD): Result<AfterSaleApproveCMD, BusinessError> =
        validateKey(command, command.idempotencyKey)

    fun validate(command: AfterSaleRejectCMD): Result<AfterSaleRejectCMD, BusinessError> =
        if (command.rejectionReason.trim().length !in 1..500)
            Failure(AfterSaleErrors.REJECTION_REASON_INVALID)
        else validateKey(command, command.idempotencyKey)

    fun validate(command: AfterSaleCancelCMD): Result<AfterSaleCancelCMD, BusinessError> =
        validateKey(command, command.idempotencyKey)

    private fun <T> validateKey(value: T, key: String): Result<T, BusinessError> =
        if (validKey(key)) Success(value) else Failure(AfterSaleErrors.IDEMPOTENCY_KEY_INVALID)

    private fun validKey(key: String): Boolean = key.trim().length in 1..128
}
