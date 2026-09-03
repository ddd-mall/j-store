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
package com.jstore.fulfillment.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.fulfillment.domain.FulfillmentOrder
import com.jstore.fulfillment.service.MerchantFulfillmentUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/fulfillments")
@RequireLogin
class FulfillmentController(private val service: MerchantFulfillmentUseCase) {
    data class DispatchRequest(val carrierCode: String, val trackingNumber: String)

    data class ErrorResponse(val message: String, val errorCode: String)

    data class Response(
        val id: Long,
        val orderId: Long,
        val merchantId: Long,
        val status: String,
        val carrierCode: String?,
        val trackingNumber: String?,
    )

    @GetMapping("/orders/{orderId}")
    fun get(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable orderId: Long,
    ): ResponseEntity<*> =
        service.get(principal.accountId.value, orderId).response {
            it.toResponse()
        }

    @PostMapping("/orders/{orderId}/prepare")
    fun prepare(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable orderId: Long,
    ): ResponseEntity<*> =
        service.prepare(principal.accountId.value, orderId).response { mapOf("changed" to it) }

    @PostMapping("/orders/{orderId}/dispatch")
    fun dispatch(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable orderId: Long,
        @RequestBody body: DispatchRequest,
    ): ResponseEntity<*> =
        service
            .dispatch(
                principal.accountId.value,
                orderId,
                body.carrierCode,
                body.trackingNumber,
            )
            .response { mapOf("changed" to it) }

    @PostMapping("/orders/{orderId}/deliver")
    fun deliver(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable orderId: Long,
    ): ResponseEntity<*> =
        service.deliver(principal.accountId.value, orderId).response { mapOf("changed" to it) }

    private fun FulfillmentOrder.toResponse() =
        Response(
            id.value,
            orderId,
            merchantId,
            status.name,
            carrierCode,
            trackingNumber,
        )

    private fun <T> Result<T, BusinessError>.response(mapper: (T) -> Any): ResponseEntity<*> =
        fold(
            onSuccess = { ResponseEntity.ok(mapper(it)) },
            onFailure = {
                ResponseEntity.status(it.httpCode).body(ErrorResponse(it.message, it.errorCode))
            },
        )
}
