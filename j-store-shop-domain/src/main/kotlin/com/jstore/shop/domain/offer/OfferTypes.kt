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
package com.jstore.shop.domain.offer

import com.jstore.common.properties.Id
import java.time.Instant

data class SalesOfferId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class StoreId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class MerchantId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class SkuId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0)
    }
}

data class FulfillmentNodeId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
    }
}

data class SaleAuthorizationId(override val value: String) : Id<String>(value) {
    init {
        require(value.isNotBlank())
    }
}

data class Channel(val channelId: String, val market: String) {
    init {
        require(channelId.isNotBlank() && market.isNotBlank())
    }
}

data class EffectivePeriod(val startsAt: Instant, val endsAt: Instant?) {
    init {
        require(endsAt == null || endsAt.isAfter(startsAt))
    }

    fun contains(now: Instant): Boolean =
        !now.isBefore(startsAt) && (endsAt == null || now.isBefore(endsAt))
}

data class PurchaseLimit(val maxQuantityPerOrder: Int) {
    init {
        require(maxQuantityPerOrder > 0)
    }
}

data class FulfillmentPolicy(
    val preferredNodeId: FulfillmentNodeId,
    val allowBackorder: Boolean,
)

enum class OfferStatus {
    ACTIVE,
    SUSPENDED,
    ENDED,
}

enum class SaleAuthorizationStatus {
    AUTHORIZED,
    RELEASED,
    EXPIRED,
}
