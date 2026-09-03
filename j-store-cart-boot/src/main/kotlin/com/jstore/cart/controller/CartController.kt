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
package com.jstore.cart.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.cart.service.*
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/carts/current")
@RequireLogin
class CartController(private val carts: CartUseCase) {
    data class AddItemRequest(
        val requestId: String,
        val skuId: Long,
        val offerId: Long,
        val quantity: Int,
        val expectedCartVersion: Long? = null,
    )

    data class SelectionRequest(
        val requestId: String,
        val expectedCartVersion: Long,
        val cartLineIds: Set<Long>,
    )

    data class RefreshRequest(val requestId: String, val expectedCartVersion: Long)

    @PostMapping("/items")
    fun add(@CurrentPrincipal user: AuthenticatedPrincipal, @RequestBody request: AddItemRequest) =
        respond(
            carts.add(
                AddCartItemCommand(
                    user.accountId.value,
                    request.requestId,
                    request.skuId,
                    request.offerId,
                    request.quantity,
                    request.expectedCartVersion,
                )
            )
        )

    @PutMapping("/selection")
    fun selection(
        @CurrentPrincipal user: AuthenticatedPrincipal,
        @RequestBody request: SelectionRequest,
    ) =
        respond(
            carts.replaceSelection(
                ReplaceCartSelectionCommand(
                    user.accountId.value,
                    request.requestId,
                    request.expectedCartVersion,
                    request.cartLineIds,
                )
            )
        )

    @PostMapping("/refresh")
    fun refresh(
        @CurrentPrincipal user: AuthenticatedPrincipal,
        @RequestBody request: RefreshRequest,
    ) = respond(carts.refresh(user.accountId.value, request.requestId, request.expectedCartVersion))

    @GetMapping
    fun current(@CurrentPrincipal user: AuthenticatedPrincipal) =
        respond(carts.current(user.accountId.value))

    private fun <T> respond(
        result: com.jstore.common.utils.Result<T, BusinessError>
    ): ResponseEntity<*> =
        when (result) {
            is Success -> ResponseEntity.ok(result.value)
            is Failure ->
                ResponseEntity.status(result.error.httpCode)
                    .body(
                        mapOf(
                            "errorCode" to result.error.errorCode,
                            "message" to result.error.message,
                        )
                    )
        }
}
