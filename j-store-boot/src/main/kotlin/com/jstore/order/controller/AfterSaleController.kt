package com.jstore.order.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
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
import com.jstore.order.domain.aftersale.command.AfterSaleApproveCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCancelCMD
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.aftersale.command.AfterSaleItemRequestCMD
import com.jstore.order.domain.aftersale.command.AfterSaleReceiveReturnCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRejectCMD
import com.jstore.order.domain.aftersale.command.AfterSaleRetryRefundCMD
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.service.AfterSaleApplicationService
import com.jstore.order.service.AfterSaleOrderAccess
import com.jstore.shop.domain.merchant.MerchantId as ShopMerchantId
import com.jstore.shop.domain.merchant.MerchantPermission
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.user.domain.useraccount.UserId
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
    private val service: AfterSaleApplicationService,
    private val merchantAuthorization: MerchantAuthorizationService,
) {
    data class ItemRequest(
        @field:Positive val orderItemId: Long,
        @field:Positive val quantity: Int,
        @field:Positive val amount: Long,
        @field:NotBlank val currency: String = "CNY",
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
        @CurrentUserId userId: UserId,
        @RequestHeader("Idempotency-Key") @NotBlank key: String,
        @Valid @RequestBody body: CreateRequest,
    ) =
        service
            .create(
                AfterSaleCreateCMD(
                    OrderId(body.orderId),
                    ApplicantActorId(userId.value),
                    RefundReason(body.category, body.description),
                    body.items.map {
                        AfterSaleItemRequestCMD(
                            OrderItemId(it.orderItemId),
                            it.quantity,
                            Price.ofFen(it.amount),
                            it.currency,
                        )
                    },
                    key,
                )
            )
            .response()

    @GetMapping("/{id}")
    fun get(@CurrentUserId userId: UserId, @PathVariable id: Long): ResponseEntity<*> =
        authorizeRead(userId, service.findById(AfterSaleId(id))).response()

    @GetMapping
    fun list(
        @CurrentUserId userId: UserId,
        @RequestParam orderId: Long,
    ): ResponseEntity<*> =
        service
            .listByOrderForAccess(OrderId(orderId))
            .fold(
                onSuccess = { access ->
                    if (canRead(userId, access)) ResponseEntity.ok(access.afterSales.map(::map))
                    else notFoundResponse()
                },
                onFailure = { it.errorResponse() },
            )

    @PostMapping("/{id}/approve")
    fun approve(
        @CurrentUserId userId: UserId,
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") key: String,
    ) =
        merchantOperation(userId, AfterSaleId(id)) {
            service.approve(AfterSaleApproveCMD(AfterSaleId(id), it.merchantId, key))
        }

    @PostMapping("/{id}/reject")
    fun reject(
        @CurrentUserId userId: UserId,
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") key: String,
        @Valid @RequestBody body: RejectRequest,
    ) =
        merchantOperation(userId, AfterSaleId(id)) {
            service.reject(
                AfterSaleRejectCMD(AfterSaleId(id), it.merchantId, body.rejectionReason, key)
            )
        }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @CurrentUserId userId: UserId,
        @PathVariable id: Long,
        @RequestHeader("Idempotency-Key") key: String,
    ) =
        service
            .cancel(AfterSaleCancelCMD(AfterSaleId(id), ApplicantActorId(userId.value), key))
            .response()

    @PostMapping("/{id}/receive-return")
    fun receiveReturn(@CurrentUserId userId: UserId, @PathVariable id: Long) =
        merchantOperation(userId, AfterSaleId(id)) {
            service.receiveReturn(AfterSaleReceiveReturnCMD(AfterSaleId(id), it.merchantId))
        }

    @PostMapping("/{id}/retry-refund")
    fun retryRefund(@CurrentUserId userId: UserId, @PathVariable id: Long) =
        merchantOperation(userId, AfterSaleId(id)) {
            service.retryRefund(AfterSaleRetryRefundCMD(AfterSaleId(id), it.merchantId))
        }

    private fun merchantOperation(
        userId: UserId,
        afterSaleId: AfterSaleId,
        operation: (AfterSale) -> Result<AfterSale, BusinessError>,
    ): ResponseEntity<*> =
        service
            .findById(afterSaleId)
            .fold(
                onSuccess = { afterSale ->
                    if (
                        merchantAuthorization.hasPermission(
                            userId.value,
                            ShopMerchantId(afterSale.merchantId.value),
                            MerchantPermission.AFTER_SALE_MANAGE,
                        )
                    ) {
                        operation(afterSale).response()
                    } else {
                        notFoundResponse()
                    }
                },
                onFailure = { it.errorResponse() },
            )

    private fun authorizeRead(
        userId: UserId,
        result: Result<AfterSale, BusinessError>,
    ): Result<AfterSale, BusinessError> =
        result.fold(
            onSuccess = {
                if (
                    it.applicantId.value == userId.value ||
                        merchantAuthorization.hasPermission(
                            userId.value,
                            ShopMerchantId(it.merchantId.value),
                            MerchantPermission.AFTER_SALE_READ,
                        )
                )
                    result
                else Failure(AfterSaleErrors.NOT_FOUND)
            },
            onFailure = { result },
        )

    private fun canRead(userId: UserId, access: AfterSaleOrderAccess): Boolean =
        access.buyerId == userId.value ||
            merchantAuthorization.hasPermission(
                userId.value,
                ShopMerchantId(access.merchantId.value),
                MerchantPermission.AFTER_SALE_READ,
            )

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
