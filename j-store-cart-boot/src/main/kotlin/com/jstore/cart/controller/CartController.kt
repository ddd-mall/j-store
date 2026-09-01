package com.jstore.cart.controller

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.cart.service.*
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.UserId
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/carts/current")
@RequireLogin
class CartController(private val carts: CartUseCase) {
    data class AddItemRequest(val requestId: String, val skuId: Long, val offerId: Long, val quantity: Int, val expectedCartVersion: Long? = null)
    data class SelectionRequest(val requestId: String, val expectedCartVersion: Long, val cartLineIds: Set<Long>)
    data class RefreshRequest(val requestId: String, val expectedCartVersion: Long)

    @PostMapping("/items") fun add(@CurrentUserId user: UserId, @RequestBody request: AddItemRequest) = respond(carts.add(AddCartItemCommand(user.value, request.requestId, request.skuId, request.offerId, request.quantity, request.expectedCartVersion)))
    @PutMapping("/selection") fun selection(@CurrentUserId user: UserId, @RequestBody request: SelectionRequest) = respond(carts.replaceSelection(ReplaceCartSelectionCommand(user.value, request.requestId, request.expectedCartVersion, request.cartLineIds)))
    @PostMapping("/refresh") fun refresh(@CurrentUserId user: UserId, @RequestBody request: RefreshRequest) = respond(carts.refresh(user.value, request.requestId, request.expectedCartVersion))
    @GetMapping fun current(@CurrentUserId user: UserId) = respond(carts.current(user.value))

    private fun <T> respond(result: com.jstore.common.utils.Result<T, BusinessError>): ResponseEntity<*> = when (result) {
        is Success -> ResponseEntity.ok(result.value)
        is Failure -> ResponseEntity.status(result.error.httpCode).body(mapOf("errorCode" to result.error.errorCode, "message" to result.error.message))
    }
}
