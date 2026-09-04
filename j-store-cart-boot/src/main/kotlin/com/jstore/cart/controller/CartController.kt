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
import com.jstore.cart.service.CartUseCase
import com.jstore.cart.service.ReplaceCartSelectionCommand
import com.jstore.cart.service.SetCartItemQuantityCommand
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/carts/current")
@RequireLogin
class CartController(private val carts: CartUseCase) {
    data class SetItemQuantityRequest(
        val skuId: Long,
        val offerId: Long,
        val targetQuantity: Int,
        val expectedCartVersion: Long,
    )

    data class SelectionRequest(
        val expectedCartVersion: Long,
        val cartLineIds: Set<Long>,
    )

    data class RefreshRequest(val expectedCartVersion: Long)

    @PutMapping("/items")
    fun setItemQuantity(
        @CurrentPrincipal user: AuthenticatedPrincipal,
        @RequestBody request: SetItemQuantityRequest,
    ) =
        respond(
            result =
                carts.setItemQuantity(
                    command =
                        SetCartItemQuantityCommand(
                            buyerId = user.accountId.value,
                            skuId = request.skuId,
                            offerId = request.offerId,
                            targetQuantity = request.targetQuantity,
                            expectedCartVersion = request.expectedCartVersion,
                        )
                )
        )

    @PutMapping("/selection")
    fun selection(
        @CurrentPrincipal user: AuthenticatedPrincipal,
        @RequestBody request: SelectionRequest,
    ): ResponseEntity<*> =
        respond(
            result =
                carts.replaceSelection(
                    command =
                        ReplaceCartSelectionCommand(
                            buyerId = user.accountId.value,
                            expectedCartVersion = request.expectedCartVersion,
                            cartLineIds = request.cartLineIds,
                        )
                )
        )

    @PostMapping("/refresh")
    fun refresh(
        @CurrentPrincipal user: AuthenticatedPrincipal,
        @RequestBody request: RefreshRequest,
    ): ResponseEntity<*> =
        respond(
            result =
                carts.refresh(
                    buyerId = user.accountId.value,
                    expectedVersion = request.expectedCartVersion,
                )
        )

    @GetMapping
    fun current(@CurrentPrincipal user: AuthenticatedPrincipal) =
        respond(result = carts.current(buyerId = user.accountId.value))

    private fun <T> respond(result: Result<T, BusinessError>): ResponseEntity<*> =
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
