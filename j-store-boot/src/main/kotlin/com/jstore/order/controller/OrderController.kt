package com.jstore.order.controller

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.order.domain.order.*
import com.jstore.order.domain.order.command.*
import com.jstore.order.service.OrderService
import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/orders")
@RequireLogin
class OrderController(
    private val orderService: OrderService,
) {

    // ---- Request DTOs ----

    data class CreateOrderRequest(
        val recipientInfo: RecipientInfoRequest,
        val items: List<OrderItemRequest>,
    )

    data class RecipientInfoRequest(
        val consigneeName: String,
        val countryCode: String? = null,
        val contactPhone: String? = null,
        val contactEmail: String? = null,
        val shippingDistrictCode: String,
        val shippingDetailAddress: String,
    )

    data class OrderItemRequest(
        val spuId: Long,
        val skuId: Long,
        val quantity: Int,
    )

    data class CancelOrderRequest(
        val category: CancellationCategory,
        val description: String,
    )

    data class RequestRefundRequest(
        val category: RefundCategory,
        val description: String,
        val itemIds: List<Long>,
    )

    // ---- Response DTOs ----

    data class OrderResponse(
        val id: Long,
        val buyerUid: Long,
        val buyerPhone: String?,
        val buyerName: String?,
        val status: String,
        val totalAmount: Long,
        val actualPay: Long,
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
        @CurrentUserId userId: Long,
        @RequestBody request: CreateOrderRequest,
    ): ResponseEntity<*> {
        val cmd = OrderCreateCMD(
            buyerUid = userId,
            buyerPhone = null,
            buyerName = null,
            recipientInfo = OrderCreateCMD.RecipientInfoCMD(
                consigneeName = request.recipientInfo.consigneeName,
                countryCode = request.recipientInfo.countryCode,
                consigneeContractInfo = OrderCreateCMD.ContractInfoCMD(
                    phoneNumber = request.recipientInfo.contactPhone?.let { PhoneNumber(it) },
                    emailAddress = request.recipientInfo.contactEmail,
                ),
                shippingDistrictCode = request.recipientInfo.shippingDistrictCode,
                shippingDetailAddress = request.recipientInfo.shippingDetailAddress,
            ),
            items = request.items.map {
                OrderCreateCMD.OrderItemCMD(
                    spuId = it.spuId,
                    skuId = it.skuId,
                    quantity = it.quantity,
                )
            },
        )
        return orderService.createOrder(cmd).toResponse { it.toOrderResponse() }
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @CurrentUserId userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<*> {
        return orderService.getOrderById(OrderId(orderId)).toResponse { it.toOrderResponse() }
    }

    @GetMapping
    fun listMyOrders(
        @CurrentUserId userId: Long,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<*> {
        val result = orderService.pageListByUserId(userId, page, size)
        return ResponseEntity.ok(
            PageResponse(
                current = result.current(),
                size = result.size(),
                records = result.record().map { it.toOrderResponse() },
            )
        )
    }

    @PostMapping("/{orderId}/cancel")
    fun cancelOrder(
        @CurrentUserId userId: Long,
        @PathVariable orderId: Long,
        @RequestBody request: CancelOrderRequest,
    ): ResponseEntity<*> {
        val cmd = OrderCancelCMD(
            orderId = OrderId(orderId),
            category = request.category,
            description = request.description,
        )
        return orderService.cancelOrder(cmd).toResponse { }
    }

    @PostMapping("/{orderId}/confirm-delivery")
    fun confirmDelivery(
        @CurrentUserId userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<*> {
        return orderService.confirmDelivery(OrderId(orderId)).toResponse { }
    }

    @PostMapping("/{orderId}/refund")
    fun requestRefund(
        @CurrentUserId userId: Long,
        @PathVariable orderId: Long,
        @RequestBody request: RequestRefundRequest,
    ): ResponseEntity<*> {
        val cmd = OrderRequestRefundCMD(
            orderId = OrderId(orderId),
            category = request.category,
            description = request.description,
            itemIds = request.itemIds.map { OrderItemId(it) },
        )
        return orderService.requestRefund(cmd).toResponse { }
    }

    // ---- 卖家/管理接口 ----

    @PostMapping("/{orderId}/confirm-shipment")
    fun confirmForShipment(
        @PathVariable orderId: Long,
    ): ResponseEntity<*> {
        return orderService.confirmForShipment(OrderId(orderId)).toResponse { }
    }

    @PostMapping("/{orderId}/ship")
    fun shipOrder(
        @PathVariable orderId: Long,
    ): ResponseEntity<*> {
        return orderService.shipOrder(OrderId(orderId)).toResponse { }
    }

    @PostMapping("/{orderId}/complete")
    fun completeOrder(
        @PathVariable orderId: Long,
    ): ResponseEntity<*> {
        return orderService.completeOrder(OrderId(orderId)).toResponse { }
    }

    @PostMapping("/{orderId}/approve-refund")
    fun approveRefund(
        @PathVariable orderId: Long,
        @RequestBody request: ApproveRefundRequest,
    ): ResponseEntity<*> {
        val cmd = OrderApproveRefundCMD(
            orderId = OrderId(orderId),
            itemIds = request.itemIds.map { OrderItemId(it) },
        )
        return orderService.approveRefund(cmd).toResponse { }
    }

    data class ApproveRefundRequest(
        val itemIds: List<Long>,
    )

    @PostMapping("/{orderId}/reject-refund")
    fun rejectRefund(
        @PathVariable orderId: Long,
        @RequestBody request: RejectRefundRequest,
    ): ResponseEntity<*> {
        val cmd = OrderRejectRefundCMD(
            orderId = OrderId(orderId),
            rejectReason = request.rejectReason,
            itemIds = request.itemIds.map { OrderItemId(it) },
        )
        return orderService.rejectRefund(cmd).toResponse { }
    }

    data class RejectRefundRequest(
        val rejectReason: String,
        val itemIds: List<Long>,
    )

    // ---- 支付回调接口（内部/系统调用） ----

    @PostMapping("/{orderId}/pay-callback")
    fun payCallback(
        @PathVariable orderId: Long,
        @RequestBody request: PayCallbackRequest,
    ): ResponseEntity<*> {
        val cmd = OrderPayCMD(
            orderId = OrderId(orderId),
            paidAmount = Price.ofFen(request.paidAmountFen),
        )
        return orderService.payOrder(cmd).toResponse { }
    }

    data class PayCallbackRequest(
        val paidAmountFen: Long,
    )

    // ---- Helpers ----

    private fun Order.toOrderResponse() = OrderResponse(
        id = id.value,
        buyerUid = buyerInfo.uid,
        buyerPhone = buyerInfo.phoneNumber?.value,
        buyerName = buyerInfo.userName,
        status = status.name,
        totalAmount = totalAmount.fen,
        actualPay = actualPay.fen,
        items = items.map { it.toOrderItemResponse() },
        createTime = createTime,
        updateTime = updateTime,
    )

    private fun OrderItem.toOrderItemResponse() = OrderItemResponse(
        id = id.value,
        skuId = skuId,
        spuId = spuId,
        goodsName = goodsName,
        skuDescription = skuDescription,
        quantity = quantity,
        unitPrice = unitPrice.fen,
        status = status.name,
    )

    private fun <T> Result<T, BusinessError>.toResponse(mapper: (T) -> Any): ResponseEntity<*> {
        return fold(
            onSuccess = { ResponseEntity.ok(mapper(it)) },
            onFailure = { error ->
                ResponseEntity.status(error.httpCode).body(
                    ErrorResponse(message = error.message, errorCode = error.errorCode)
                )
            },
        )
    }
}