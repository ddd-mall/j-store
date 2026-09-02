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
package com.jstore.order.domain.aftersale

import com.jstore.common.currency.CurrencyCode
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.OrderItemId
import java.time.LocalDateTime

enum class RefundCategory {
    NO_LONGER_NEEDED,
    NOT_AS_DESCRIBED,
    QUALITY_ISSUE,
    OTHER,
}

data class RefundReason(val category: RefundCategory, val description: String) {
    init {
        require(description.isNotBlank() && description.length <= 500)
    }
}

data class FulfillmentSnapshot(val status: FulfillmentStatus, val requireReturn: Boolean) {
    init {
        require(
            requireReturn ==
                (status == FulfillmentStatus.SHIPPED || status == FulfillmentStatus.DELIVERED)
        )
    }
}

data class GoodsSnapshot(
    val skuId: Long,
    val spuId: Long,
    val goodsName: String,
    val skuDescription: String,
)

data class RefundEligibilitySnapshot(
    val orderItemId: OrderItemId,
    val refundableQuantity: Int,
    val refundableAmount: Price,
    val currency: String,
    val goods: GoodsSnapshot,
) {
    init {
        require(refundableQuantity > 0)
        require(refundableAmount > Price.ZERO)
        require(CurrencyCode.isValid(currency)) { "currency must be a valid ISO 4217 code" }
    }
}

data class ReviewDecision(
    val reviewerId: MerchantActorId,
    val reviewedAt: LocalDateTime,
    val rejectionReason: String?,
)
