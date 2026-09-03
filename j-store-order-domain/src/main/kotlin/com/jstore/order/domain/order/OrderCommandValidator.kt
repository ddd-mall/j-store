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
package com.jstore.order.domain.order

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.command.OrderCreateCMD

/** Validates order requests without adding behavior to command data carriers. */
object OrderCommandValidator {
    fun validate(command: OrderCreateCMD): Result<OrderCreateCMD, BusinessError> {
        if (command.items.isEmpty()) return Failure(OrderErrors.ITEMS_EMPTY)
        if (command.buyerUid <= 0) return Failure(OrderErrors.BUYER_INVALID)
        if (command.merchantId <= 0) return Failure(OrderErrors.MERCHANT_INVALID)
        validate(command.recipientInfo).onFailure {
            return Failure(it)
        }
        return Success(command)
    }

    fun validate(
        recipient: OrderCreateCMD.RecipientInfoCMD
    ): Result<OrderCreateCMD.RecipientInfoCMD, BusinessError> {
        if (recipient.consigneeName.isBlank()) return Failure(OrderErrors.CONSIGNEE_NAME_BLANK)
        if (recipient.countryCode.isBlank()) return Failure(OrderErrors.COUNTRY_CODE_BLANK)
        if (recipient.shippingDistrictCode.isBlank())
            return Failure(OrderErrors.DISTRICT_CODE_BLANK)
        validate(recipient.consigneeContractInfo).onFailure {
            return Failure(it)
        }
        return Success(recipient)
    }

    fun validate(
        contact: OrderCreateCMD.ContractInfoCMD
    ): Result<OrderCreateCMD.ContractInfoCMD, BusinessError> =
        if (contact.phoneNumber == null && contact.emailAddress == null)
            Failure(OrderErrors.CONTRACT_INFO_INVALID.msg("收货人联系方式不能全为空"))
        else Success(contact)

    fun cancellationReason(command: OrderCancelCMD): Result<CancellationReason, BusinessError> =
        if (command.description.isBlank()) Failure(OrderErrors.CANCEL_REASON_INVALID)
        else Success(CancellationReason(command.category, command.description))
}
