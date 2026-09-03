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
package com.jstore.order.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.order.domain.aftersale.AfterSale
import com.jstore.order.domain.aftersale.AfterSaleErrors
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.ApplicantActorId
import com.jstore.order.domain.aftersale.FulfillmentSnapshot
import com.jstore.order.domain.aftersale.RefundCategory
import com.jstore.order.domain.aftersale.RefundReason
import com.jstore.order.domain.aftersale.ReviewDecision
import com.jstore.order.domain.aftersale.command.AfterSaleCancelCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.aftersale.command.AfterSaleItemRequestCMD
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.service.AfterSaleAccessUseCase
import com.jstore.order.service.AfterSaleUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.LocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/after-sales")
@RequireLogin
class AfterSaleController(
    private val service: AfterSaleUseCase,
    private val access: AfterSaleAccessUseCase,
) {
    data class ItemRequest(
        @field:Positive val orderItemId: Long,
        @field:Positive val quantity: Int,
        @field:Positive val amount: Long,
    )

    data class CreateRequest(
        @field:Positive val orderId: Long,
        val category: RefundCategory,
        @field:Size(max = 500) val description: String,
        @field:NotEmpty @field:Valid val items: List<ItemRequest>,
    )

    data class RejectRequest(@field:NotBlank @field:Size(max = 500) val rejectionReason: String)

    data class ErrorResponse(val message: String, val errorCode: String)

    data class ItemResponse(
        val id: Long,
        val orderItemId: Long,
        val requestedQuantity: Int,
        val requestedAmount: Long,
        val currency: String,
        val eligibleQuantity: Int,
        val eligibleAmount: Long,
        val skuId: Long,
        val spuId: Long,
        val goodsName: String,
        val skuDescription: String,
    )

    data class Response(
        val id: Long,
        val orderId: Long,
        val applicantId: Long,
        val merchantId: Long,
        val status: String,
        val reason: RefundReason,
        val fulfillmentSnapshot: FulfillmentSnapshot,
        val items: List<ItemResponse>,
        val reviewDecision: ReviewDecision?,
        val cancelledAt: LocalDateTime?,
        val returnReceivedAt: LocalDateTime?,
        val refundId: String?,
        val refundFailureReason: String?,
        val createTime: LocalDateTime,
        val updateTime: LocalDateTime,
    )

    @PostMapping
    fun create(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @RequestHeader("Idempotency-Key") @NotBlank key: String,
        @Valid @RequestBody body: CreateRequest,
    ) =
        service
            .create(
                principal.authenticationDomain,
                AfterSaleCreateCMD(
                    OrderId(body.orderId),
                    ApplicantActorId(principal.accountId.value),
                    RefundReason(body.category, body.description),
                    body.items.map {
                        AfterSaleItemRequestCMD(
                            OrderItemId(it.orderItemId),
                            it.quantity,
                            Price.ofFen(it.amount),
                        )
                    },
                    key,
                ),
            )
            .response()

    @GetMapping("/{id}")
    fun get(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<*> =
        access
            .get(principal.authenticationDomain, principal.accountId.value, AfterSaleId(id))
            .response()

    @GetMapping
    fun list(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @RequestParam orderId: Long,
    ): ResponseEntity<*> =
        access
            .list(principal.authenticationDomain, principal.accountId.value, OrderId(orderId))
            .response { it.map(::map) }

    @PostMapping("/{id}/approve")
    fun approve(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") key: String,
    ) = access.approve(principal.accountId.value, AfterSaleId(id), key).response()

    @PostMapping("/{id}/reject")
    fun reject(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") key: String,
        @Valid @RequestBody body: RejectRequest,
    ) =
        access
            .reject(principal.accountId.value, AfterSaleId(id), body.rejectionReason, key)
            .response()

    @PostMapping("/{id}/cancel")
    fun cancel(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") key: String,
    ) =
        service
            .cancel(
                principal.authenticationDomain,
                AfterSaleCancelCMD(
                    AfterSaleId(id),
                    ApplicantActorId(principal.accountId.value),
                    key,
                ),
            )
            .response()

    @PostMapping("/{id}/receive-return")
    fun receiveReturn(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable id: Long,
    ) = access.receiveReturn(principal.accountId.value, AfterSaleId(id)).response()

    @PostMapping("/{id}/retry-refund")
    fun retryRefund(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable id: Long,
    ) = access.retryRefund(principal.accountId.value, AfterSaleId(id)).response()

    private fun Result<AfterSale, BusinessError>.response() = response(::map)

    private fun <T> Result<T, BusinessError>.response(mapper: (T) -> Any): ResponseEntity<*> =
        fold(
            onSuccess = { ResponseEntity.ok(mapper(it)) },
            onFailure = { it.errorResponse() },
        )

    private fun BusinessError.errorResponse(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(httpCode).body(ErrorResponse(message, errorCode))

    private fun notFoundResponse(): ResponseEntity<ErrorResponse> =
        AfterSaleErrors.NOT_FOUND.errorResponse()

    private fun map(afterSale: AfterSale) =
        Response(
            afterSale.id.value,
            afterSale.orderId.value,
            afterSale.applicantId.value,
            afterSale.merchantId.value,
            afterSale.status.name,
            afterSale.reason,
            afterSale.fulfillmentSnapshot,
            afterSale.items.map {
                ItemResponse(
                    it.id.value,
                    it.orderItemId.value,
                    it.requestedQuantity,
                    it.requestedAmount.fen,
                    it.currency,
                    it.eligibilitySnapshot.refundableQuantity,
                    it.eligibilitySnapshot.refundableAmount.fen,
                    it.eligibilitySnapshot.goods.skuId,
                    it.eligibilitySnapshot.goods.spuId,
                    it.eligibilitySnapshot.goods.goodsName,
                    it.eligibilitySnapshot.goods.skuDescription,
                )
            },
            afterSale.reviewDecision,
            afterSale.cancelledAt,
            afterSale.returnReceivedAt,
            afterSale.refundId,
            afterSale.refundFailureReason,
            afterSale.createTime,
            afterSale.updateTime,
        )
}
