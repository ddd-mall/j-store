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

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.order.domain.order.*
import com.jstore.order.domain.order.command.*
import com.jstore.order.service.OrderUseCase
import com.jstore.user.domain.useraccount.UserId
import java.time.LocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
@RequireLogin
class OrderController(private val orderService: OrderUseCase) {

    // ---- Request DTOs ----

    data class CreateOrderRequest(
        val merchantId: Long,
        val recipientInfo: RecipientInfoRequest,
        val items: List<OrderItemRequest>,
    )

    data class RecipientInfoRequest(
        val consigneeName: String,
        val countryCode: String,
        val contactPhone: String? = null,
        val contactEmail: String? = null,
        val shippingDistrictCode: String,
        val shippingDetailAddress: String,
        val postalCode: String? = null,
        val customsFields: Map<String, String> = emptyMap(),
    )

    data class OrderItemRequest(
        val spuId: Long,
        val skuId: Long,
        val quantity: Int,
        val snapshotVersion: Long,
    )

    data class CancelOrderRequest(
        val category: CancellationCategory,
        val description: String,
    )

    // ---- Response DTOs ----

    data class OrderResponse(
        val id: Long,
        val merchantId: Long,
        val buyerUid: Long,
        val buyerPhone: String?,
        val buyerName: String?,
        val tradeStatus: String,
        val paymentStatus: String,
        val fulfillmentStatus: String,
        val currency: String,
        val itemsSubtotal: Long,
        val discountAmount: Long,
        val shippingAmount: Long,
        val taxAmount: Long,
        val payableAmount: Long,
        val paidAmount: Long,
        val refundedAmount: Long,
        val items: List<OrderItemResponse>,
        val createTime: LocalDateTime,
        val updateTime: LocalDateTime,
    )

    data class OrderItemResponse(
        val id: Long,
        val skuId: Long,
        val spuId: Long,
        val goodsName: String,
        val skuDescription: String,
        val quantity: Int,
        val unitPrice: Long,
        val status: String,
        val refundedQuantity: Int,
        val refundedAmount: Long,
    )

    data class PageResponse<T>(
        val current: Int,
        val size: Int,
        val records: Collection<T>,
    )

    data class ErrorResponse(
        val message: String,
        val errorCode: String,
    )

    // ---- 买家接口 ----

    @PostMapping
    fun createOrder(
        @CurrentUserId userId: UserId,
        @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<*> {
        val cmd =
            OrderCreateCMD(
                buyerUid = userId.value,
                merchantId = request.merchantId,
                buyerPhone = null,
                buyerName = null,
                recipientInfo =
                    OrderCreateCMD.RecipientInfoCMD(
                        consigneeName = request.recipientInfo.consigneeName,
                        countryCode = request.recipientInfo.countryCode,
                        consigneeContractInfo =
                            OrderCreateCMD.ContractInfoCMD(
                                phoneNumber =
                                    request.recipientInfo.contactPhone?.let { PhoneNumber(it) },
                                emailAddress = request.recipientInfo.contactEmail,
                            ),
                        shippingDistrictCode = request.recipientInfo.shippingDistrictCode,
                        shippingDetailAddress = request.recipientInfo.shippingDetailAddress,
                        postalCode = request.recipientInfo.postalCode,
                        customsFields = request.recipientInfo.customsFields,
                    ),
                items =
                    request.items.map {
                        OrderCreateCMD.OrderItemCMD(
                            spuId = it.spuId,
                            skuId = it.skuId,
                            quantity = it.quantity,
                            snapshotVersion = it.snapshotVersion,
                        )
                    },
            )
        return orderService.createOrder(cmd).toResponse { it.toOrderResponse() }
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @CurrentUserId userId: UserId,
        @PathVariable orderId: Long,
    ): ResponseEntity<*> {
        return orderService.getOrderById(OrderId(orderId)).toResponse { it.toOrderResponse() }
    }

    @GetMapping
    fun listMyOrders(
        @CurrentUserId userId: UserId,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<*> {
        val result = orderService.pageListByUserId(userId.value, page, size)
        return ResponseEntity.ok(
            PageResponse(
                current = result.currentPage,
                size = result.totalElements,
                records = result.records.map { it.toOrderResponse() },
            )
        )
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @CurrentUserId userId: UserId,
        @PathVariable orderId: Long,
        @RequestBody request: CancelOrderRequest,
    ): ResponseEntity<*> {
        val cmd =
            OrderCancelCMD(
                orderId = OrderId(orderId),
                category = request.category,
                description = request.description,
            )
        return orderService.cancelOrder(cmd).toResponse {}
    }

    // ---- Helpers ----

    private fun Order.toOrderResponse() =
        OrderResponse(
            id = id.value,
            merchantId = merchantId.value,
            buyerUid = buyerInfo.uid,
            buyerPhone = buyerInfo.phoneNumber?.value,
            buyerName = buyerInfo.userName,
            tradeStatus = tradeStatus.name,
            paymentStatus = paymentStatus.name,
            fulfillmentStatus = fulfillmentStatus.name,
            currency = amountSnapshot.currency,
            itemsSubtotal = amountSnapshot.itemsSubtotal.fen,
            discountAmount = amountSnapshot.discountAmount.fen,
            shippingAmount = amountSnapshot.shippingAmount.fen,
            taxAmount = amountSnapshot.taxAmount.fen,
            payableAmount = amountSnapshot.payableAmount.fen,
            paidAmount = paidAmount.fen,
            refundedAmount = refundedAmount.fen,
            items = items.map { it.toOrderItemResponse() },
            createTime = createTime,
            updateTime = updateTime,
        )

    private fun OrderItem.toOrderItemResponse() =
        OrderItemResponse(
            id = id.value,
            skuId = skuId,
            spuId = spuId,
            goodsName = goodsName,
            skuDescription = skuDescription,
            quantity = quantity,
            unitPrice = unitPrice.fen,
            status = status.name,
            refundedQuantity = refundedQuantity,
            refundedAmount = refundedAmount.fen,
        )

    private fun <T> Result<T, BusinessError>.toResponse(mapper: (T) -> Any): ResponseEntity<*> {
        return fold(
            onSuccess = { ResponseEntity.ok(mapper(it)) },
            onFailure = { error ->
                ResponseEntity.status(error.httpCode)
                    .body(ErrorResponse(message = error.message, errorCode = error.errorCode))
            },
        )
    }
}
