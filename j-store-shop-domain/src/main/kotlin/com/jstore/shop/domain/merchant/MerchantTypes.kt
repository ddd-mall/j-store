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
package com.jstore.shop.domain.merchant

import com.jstore.common.properties.Id

data class MerchantId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "merchantId must be positive" }
    }
}

data class MerchantMembershipId(override val value: Long) : Id<Long>(value) {
    init {
        require(value > 0) { "merchantMembershipId must be positive" }
    }
}

enum class MerchantStatus {
    ACTIVE,
    DISABLED,
}

enum class MerchantMembershipStatus {
    ACTIVE,
    DISABLED,
}

enum class MerchantPermission {
    MERCHANT_READ,
    MEMBER_MANAGE,
    GOODS_READ,
    GOODS_MANAGE,
    ORDER_READ,
    AFTER_SALE_READ,
    AFTER_SALE_MANAGE,
    PAYMENT_READ,
    PAYMENT_MANAGE,
    FULFILLMENT_READ,
    FULFILLMENT_MANAGE,
    FINANCE_READ,
}

enum class MerchantRole(val permissions: Set<MerchantPermission>) {
    OWNER(MerchantPermission.entries.toSet()),
    ADMIN(MerchantPermission.entries.toSet()),
    PRODUCT_MANAGER(
        setOf(
            MerchantPermission.MERCHANT_READ,
            MerchantPermission.GOODS_READ,
            MerchantPermission.GOODS_MANAGE,
        )
    ),
    ORDER_MANAGER(
        setOf(
            MerchantPermission.MERCHANT_READ,
            MerchantPermission.ORDER_READ,
            MerchantPermission.AFTER_SALE_READ,
            MerchantPermission.AFTER_SALE_MANAGE,
            MerchantPermission.FULFILLMENT_READ,
            MerchantPermission.FULFILLMENT_MANAGE,
        )
    ),
    CUSTOMER_SERVICE(
        setOf(
            MerchantPermission.MERCHANT_READ,
            MerchantPermission.ORDER_READ,
            MerchantPermission.AFTER_SALE_READ,
            MerchantPermission.AFTER_SALE_MANAGE,
        )
    ),
    FINANCE(
        setOf(
            MerchantPermission.MERCHANT_READ,
            MerchantPermission.ORDER_READ,
            MerchantPermission.PAYMENT_READ,
            MerchantPermission.PAYMENT_MANAGE,
            MerchantPermission.FINANCE_READ,
        )
    ),
    VIEWER(
        setOf(
            MerchantPermission.MERCHANT_READ,
            MerchantPermission.GOODS_READ,
            MerchantPermission.ORDER_READ,
            MerchantPermission.AFTER_SALE_READ,
            MerchantPermission.PAYMENT_READ,
            MerchantPermission.FULFILLMENT_READ,
            MerchantPermission.FINANCE_READ,
        )
    ),
}
