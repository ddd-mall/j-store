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
