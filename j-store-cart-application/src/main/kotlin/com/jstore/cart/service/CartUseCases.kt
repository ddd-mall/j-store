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
package com.jstore.cart.service

import com.jstore.cart.domain.*
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result

data class SetCartItemQuantityCommand(
    val buyerId: Long,
    val skuId: Long,
    val offerId: Long,
    val targetQuantity: Int,
    val expectedCartVersion: Long,
)

data class ReplaceCartSelectionCommand(
    val buyerId: Long,
    val expectedCartVersion: Long,
    val cartLineIds: Set<Long>,
)

data class CartAssessmentView(
    val sourceCartVersion: Long,
    val status: String,
    val amountFen: Long,
    val currency: String,
    val lines: List<CartAssessmentLine>,
)

data class CartView(
    val cartId: Long,
    val contentVersion: Long,
    val market: String,
    val channelId: String,
    val currency: String,
    val lines: List<CartLine>,
    val assessment: CartAssessmentView?,
)

interface CartUseCase {
    fun setItemQuantity(command: SetCartItemQuantityCommand): Result<CartView, BusinessError>

    fun replaceSelection(command: ReplaceCartSelectionCommand): Result<CartView, BusinessError>

    fun refresh(
        buyerId: Long,
        expectedVersion: Long,
    ): Result<CartView, BusinessError>

    fun current(buyerId: Long): Result<CartView, BusinessError>
}

sealed interface SetCartItemQuantityStart {
    data class Completed(val view: CartView) : SetCartItemQuantityStart

    data object RequiresOffer : SetCartItemQuantityStart
}

sealed interface CartRefreshStart {
    data class Completed(val view: CartView) : CartRefreshStart

    data class RequiresFacts(val cart: Cart) : CartRefreshStart
}

sealed interface CartCheckoutPreparationStart {
    data class Completed(val result: com.jstore.cart.api.CartCheckoutSourceResult) :
        CartCheckoutPreparationStart

    data class RequiresFacts(val cart: Cart) : CartCheckoutPreparationStart
}

fun interface CartIdentityGenerator {
    fun nextId(): Long
}
