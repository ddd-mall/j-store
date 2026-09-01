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
package com.jstore.trade.config

import com.jstore.cart.api.*
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.trade.domain.TradeErrors
import com.jstore.trade.service.CheckoutItem
import com.jstore.trade.service.CheckoutSourceGateway
import com.jstore.trade.service.CreateCheckoutCommand

class CartCheckoutSourceAdapter(private val carts: CartCheckoutSourceQueryService) : CheckoutSourceGateway {
    override fun resolve(command: CreateCheckoutCommand): Result<CreateCheckoutCommand, com.jstore.common.errors.BusinessError> {
        val cartId = command.cartId ?: return if (command.items.isNotEmpty()) Success(command) else Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        val expectedVersion = command.expectedCartVersion ?: return Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        if (command.items.isNotEmpty()) return Failure(TradeErrors.CHECKOUT_REQUEST_INVALID)
        return when (val result = carts.prepare(CartCheckoutSourceQuery(command.buyerId, cartId, expectedVersion))) {
            is CartCheckoutSourceResult.Found -> Success(command.copy(items = result.source.eligibleLines.map { CheckoutItem(it.offerId, it.offerVersion, it.spuId, it.skuId, it.quantity, it.catalogSnapshotVersion) }, cartDigest = result.source.cartDigest))
            CartCheckoutSourceResult.NotFound -> Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
            CartCheckoutSourceResult.VersionConflict -> Failure(TradeErrors.START_CONFLICT)
            CartCheckoutSourceResult.NoEligibleLines -> Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
            CartCheckoutSourceResult.Unavailable -> Failure(TradeErrors.CHECKOUT_OFFER_INVALID)
        }
    }
}
