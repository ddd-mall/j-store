package com.jstore.cart.service

import com.jstore.cart.domain.*
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

data class AddCartItemCommand(val buyerId: Long, val requestId: String, val skuId: Long, val offerId: Long, val quantity: Int, val expectedCartVersion: Long? = null)
data class ReplaceCartSelectionCommand(val buyerId: Long, val requestId: String, val expectedCartVersion: Long, val cartLineIds: Set<Long>)
data class CartAssessmentView(val sourceCartVersion: Long, val status: String, val amountFen: Long, val currency: String, val lines: List<CartAssessmentLine>)
data class CartView(val cartId: Long, val contentVersion: Long, val market: String, val channelId: String, val currency: String, val lines: List<CartLine>, val assessment: CartAssessmentView?)

interface CartUseCase {
    fun add(command: AddCartItemCommand): Result<CartView, BusinessError>
    fun replaceSelection(command: ReplaceCartSelectionCommand): Result<CartView, BusinessError>
    fun refresh(buyerId: Long, requestId: String, expectedVersion: Long): Result<CartView, BusinessError>
    fun current(buyerId: Long): Result<CartView, BusinessError>
}

fun interface CartIdentityGenerator { fun nextId(): Long }
