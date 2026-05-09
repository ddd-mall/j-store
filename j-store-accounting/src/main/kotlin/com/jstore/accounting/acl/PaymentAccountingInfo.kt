package com.jstore.accounting.acl

data class PaymentAccountingInfo(
    val paymentId: String,
    val channel: String,
    val paymentNo: String,
)
