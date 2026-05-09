package com.jstore.accounting.acl

import com.jstore.common.properties.Price
import java.time.Instant

data class OrderAccountingInfo(
    val orderId: String,
    val merchantId: String,
    val paidAmount: Price,
    val commissionAmount: Price,
    val completedAt: Instant?,
)
