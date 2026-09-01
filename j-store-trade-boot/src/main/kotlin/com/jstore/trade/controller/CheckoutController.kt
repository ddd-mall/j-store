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
package com.jstore.trade.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.trade.service.*
import com.jstore.user.domain.useraccount.UserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/checkouts")
@RequireLogin
class CheckoutController(private val checkouts: CheckoutUseCase) {
    data class CreateCheckoutRequest(
        val checkoutRequestId: String,
        val recipient: RecipientRequest,
        val items: List<ItemRequest> = emptyList(),
        val cartId: Long? = null,
        val expectedCartVersion: Long? = null,
    )

    data class RecipientRequest(
        val name: String,
        val countryCode: String,
        val phone: String?,
        val email: String?,
        val districtCode: String,
        val detailAddress: String,
        val postalCode: String? = null,
        val customsFields: Map<String, String> = emptyMap(),
    )

    data class ItemRequest(
        val offerId: Long,
        val offerVersion: Long,
        val spuId: Long,
        val skuId: Long,
        val quantity: Int,
        val catalogSnapshotVersion: Long,
    )

    data class CheckoutResponse(
        val tradeId: Long,
        val status: String,
        val statusUrl: String,
        val orderIds: List<Long>,
        val payment: PaymentResponse? = null,
    )

    data class PaymentResponse(
        val paymentId: Long,
        val status: String,
        val amountFen: Long,
        val currency: String,
        val payAction: String,
        val expiresAt: java.time.Instant,
    )

    data class ErrorResponse(val message: String, val errorCode: String)

    @PostMapping
    fun create(
        @CurrentUserId userId: UserId,
        @RequestBody request: CreateCheckoutRequest,
    ): ResponseEntity<*> =
        checkouts
            .checkout(
                CreateCheckoutCommand(
                    checkoutRequestId = request.checkoutRequestId,
                    buyerId = userId.value,
                    recipient = request.recipient.toCommand(),
                    items = request.items.map { it.toCommand() },
                    cartId = request.cartId,
                    expectedCartVersion = request.expectedCartVersion,
                )
            )
            .toResponse()

    @GetMapping("/{tradeId}")
    fun find(
        @CurrentUserId userId: UserId,
        @PathVariable tradeId: Long,
    ): ResponseEntity<*> =
        checkouts
            .find(userId.value, tradeId)
            .fold(
                onSuccess = {
                    ResponseEntity.ok(
                        CheckoutResponse(
                            tradeId = it.tradeId,
                            status = it.status,
                            statusUrl = "/api/checkouts/${it.tradeId}",
                            orderIds = it.orderIds,
                            payment = it.payment?.toResponse(),
                        )
                    )
                },
                onFailure = {
                    ResponseEntity.status(it.httpCode).body(ErrorResponse(it.message, it.errorCode))
                },
            )

    private fun RecipientRequest.toCommand() =
        CheckoutRecipient(
            name,
            countryCode,
            phone,
            email,
            districtCode,
            detailAddress,
            postalCode,
            customsFields,
        )

    private fun ItemRequest.toCommand() =
        CheckoutItem(offerId, offerVersion, spuId, skuId, quantity, catalogSnapshotVersion)

    private fun CheckoutPaymentView.toResponse() =
        PaymentResponse(paymentId, status, amountFen, currency, payAction, expiresAt)

    private fun Result<CheckoutAccepted, BusinessError>.toResponse(): ResponseEntity<*> =
        fold(
            onSuccess = {
                ResponseEntity.accepted()
                    .body(
                        CheckoutResponse(
                            tradeId = it.tradeId,
                            status = "ACCEPTED",
                            statusUrl = "/api/checkouts/${it.tradeId}",
                            orderIds = it.orderIds,
                        )
                    )
            },
            onFailure = {
                ResponseEntity.status(it.httpCode).body(ErrorResponse(it.message, it.errorCode))
            },
        )
}
