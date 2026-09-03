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
package com.jstore.order.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.AfterSale
import com.jstore.order.domain.aftersale.AfterSaleErrors
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.command.AfterSaleApproveCMD
import com.jstore.order.domain.aftersale.command.AfterSaleReceiveReturnCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRejectCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRetryRefundCMD
import com.jstore.order.domain.order.OrderId
import com.jstore.shop.api.MerchantAuthorizationQuery
import com.jstore.shop.api.MerchantCapability

interface AfterSaleAccessUseCase {
    fun get(
        authenticationDomain: String,
        accountId: Long,
        id: AfterSaleId,
    ): Result<AfterSale, BusinessError>

    fun list(
        authenticationDomain: String,
        accountId: Long,
        orderId: OrderId,
    ): Result<List<AfterSale>, BusinessError>

    fun approve(accountId: Long, id: AfterSaleId, key: String): Result<AfterSale, BusinessError>

    fun reject(
        accountId: Long,
        id: AfterSaleId,
        reason: String,
        key: String,
    ): Result<AfterSale, BusinessError>

    fun receiveReturn(accountId: Long, id: AfterSaleId): Result<AfterSale, BusinessError>

    fun retryRefund(accountId: Long, id: AfterSaleId): Result<AfterSale, BusinessError>
}

class AfterSaleAccessService(
    private val afterSales: AfterSaleUseCase,
    private val authorization: MerchantAuthorizationQuery,
) : AfterSaleAccessUseCase {
    override fun get(authenticationDomain: String, accountId: Long, id: AfterSaleId) =
        afterSales.findById(id).visibleTo(authenticationDomain, accountId)

    override fun list(
        authenticationDomain: String,
        accountId: Long,
        orderId: OrderId,
    ): Result<List<AfterSale>, BusinessError> =
        when (val result = afterSales.listByOrderForAccess(orderId)) {
            is Failure -> result
            is Success ->
                if (canRead(authenticationDomain, accountId, result.value))
                    Success(result.value.afterSales)
                else Failure(AfterSaleErrors.NOT_FOUND)
        }

    override fun approve(accountId: Long, id: AfterSaleId, key: String) =
        manage(accountId, id) { afterSales.approve(AfterSaleApproveCMD(id, it.merchantId, key)) }

    override fun reject(accountId: Long, id: AfterSaleId, reason: String, key: String) =
        manage(accountId, id) {
            afterSales.reject(AfterSaleRejectCMD(id, it.merchantId, reason, key))
        }

    override fun receiveReturn(accountId: Long, id: AfterSaleId) =
        manage(accountId, id) {
            afterSales.receiveReturn(AfterSaleReceiveReturnCMD(id, it.merchantId))
        }

    override fun retryRefund(accountId: Long, id: AfterSaleId) =
        manage(accountId, id) { afterSales.retryRefund(AfterSaleRetryRefundCMD(id, it.merchantId)) }

    private fun manage(
        accountId: Long,
        id: AfterSaleId,
        operation: (AfterSale) -> Result<AfterSale, BusinessError>,
    ): Result<AfterSale, BusinessError> =
        when (val result = afterSales.findById(id)) {
            is Failure -> result
            is Success ->
                if (
                    authorization.isAllowed(
                        accountId,
                        result.value.merchantId.value,
                        MerchantCapability.AFTER_SALE_MANAGE,
                    )
                )
                    operation(result.value)
                else Failure(AfterSaleErrors.NOT_FOUND)
        }

    private fun Result<AfterSale, BusinessError>.visibleTo(
        authenticationDomain: String,
        accountId: Long,
    ): Result<AfterSale, BusinessError> =
        when (this) {
            is Failure -> this
            is Success -> {
                val access = afterSales.listByOrderForAccess(value.orderId)
                if (access is Success && canRead(authenticationDomain, accountId, access.value))
                    this
                else Failure(AfterSaleErrors.NOT_FOUND)
            }
        }

    private fun canRead(domain: String, accountId: Long, access: AfterSaleOrderAccess): Boolean =
        (access.buyerAuthenticationDomain == domain && access.buyerId == accountId) ||
            authorization.isAllowed(
                accountId,
                access.merchantId.value,
                MerchantCapability.AFTER_SALE_READ,
            )
}
