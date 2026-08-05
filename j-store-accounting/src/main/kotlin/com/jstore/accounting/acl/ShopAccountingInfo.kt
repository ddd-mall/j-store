package com.jstore.accounting.acl

import java.math.BigDecimal

data class ShopAccountingInfo(
    val merchantId: String,
    val commissionRate: BigDecimal,
)
