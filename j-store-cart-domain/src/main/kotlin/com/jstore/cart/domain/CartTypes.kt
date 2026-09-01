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
package com.jstore.cart.domain

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.Id

data class CartId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class CartLineId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class CartAssessmentId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class BuyerId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class SkuId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class OfferId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class MerchantId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class CartRequestReceiptId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
    }
}

data class SettlementScope(val market: String, val channelId: String, val currency: String) {
    init {
        require(market.isNotBlank() && channelId.isNotBlank() && currency.length == 3)
    }
}

enum class CartStatus {
    ACTIVE,
    EXPIRED,
}

object CartErrors {
    val INVALID_QUANTITY = BusinessError("购物车数量无效", "Cart.InvalidQuantity", 400)
    val LINE_LIMIT = BusinessError("购物车行数超过限制", "Cart.LineLimitExceeded", 400)
    val SCOPE_MISMATCH = BusinessError("商品市场、渠道或币种与购物车不一致", "Cart.SettlementScopeMismatch", 409)
    val UNKNOWN_SELECTION = BusinessError("选择中包含未知购物车行", "Cart.UnknownSelectionLine", 400)
    val NOT_FOUND = BusinessError("购物车不存在", "Cart.NotFound", 404)
    val VERSION_CONFLICT = BusinessError("购物车版本冲突", "Cart.VersionConflict", 409)
    val REQUEST_CONFLICT = BusinessError("购物车请求幂等键冲突", "Cart.RequestConflict", 409)
    val OFFER_MISMATCH = BusinessError("Offer 与 SKU 不匹配", "Cart.OfferSkuMismatch", 409)
    val NO_ELIGIBLE_LINES = BusinessError("没有可结算商品", "Cart.NoEligibleLines", 409)
    val REFRESH_UNAVAILABLE = BusinessError("购物车试算暂不可用", "Cart.RefreshUnavailable", 503)
}
