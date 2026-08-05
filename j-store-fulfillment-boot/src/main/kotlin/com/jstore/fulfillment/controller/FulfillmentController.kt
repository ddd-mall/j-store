package com.jstore.fulfillment.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.fulfillment.domain.FulfillmentErrors
import com.jstore.fulfillment.domain.FulfillmentOrder
import com.jstore.fulfillment.service.FulfillmentUseCase
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantPermission
import com.jstore.shop.service.MerchantAuthorizationService
import com.jstore.user.domain.useraccount.UserId
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
class FulfillmentController(
    private val service: FulfillmentUseCase,
    private val merchantAuthorization: MerchantAuthorizationService,
) {
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
    fun get(@CurrentUserId userId: UserId, @PathVariable orderId: Long): ResponseEntity<*> =
        authorized(userId, orderId, MerchantPermission.FULFILLMENT_READ).response {
            it.toResponse()
        }

    @PostMapping("/orders/{orderId}/prepare")
    fun prepare(@CurrentUserId userId: UserId, @PathVariable orderId: Long): ResponseEntity<*> =
        authorizeThen(userId, orderId) { service.prepare(orderId) }

    @PostMapping("/orders/{orderId}/dispatch")
    fun dispatch(
        @CurrentUserId userId: UserId,
        @PathVariable orderId: Long,
        @RequestBody body: DispatchRequest,
    ): ResponseEntity<*> =
        authorizeThen(userId, orderId) {
            service.dispatch(orderId, body.carrierCode, body.trackingNumber)
        }

    @PostMapping("/orders/{orderId}/deliver")
    fun deliver(@CurrentUserId userId: UserId, @PathVariable orderId: Long): ResponseEntity<*> =
        authorizeThen(userId, orderId) { service.deliver(orderId) }

    private fun authorizeThen(
        userId: UserId,
        orderId: Long,
        operation: () -> Result<Boolean, BusinessError>,
    ): ResponseEntity<*> {
        val authorization = authorized(userId, orderId, MerchantPermission.FULFILLMENT_MANAGE)
        if (authorization is Failure) return authorization.response {}
        return operation().response { mapOf("changed" to it) }
    }

    private fun authorized(
        userId: UserId,
        orderId: Long,
        permission: MerchantPermission,
    ): Result<FulfillmentOrder, BusinessError> {
        val result = service.getByOrderId(orderId)
        return result.fold(
            onSuccess = {
                if (
                    merchantAuthorization.hasPermission(
                        userId.value,
                        MerchantId(it.merchantId),
                        permission,
                    )
                )
                    result
                else Failure(FulfillmentErrors.NOT_FOUND)
            },
            onFailure = { result },
        )
    }

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
